package org.atomhopper;

import com.rackspace.papi.commons.util.thread.Poller;
import com.rackspace.papi.commons.util.thread.RecurringTask;
import org.apache.abdera.Abdera;
import org.apache.abdera.ext.json.JSONFilter;
import org.apache.abdera.protocol.server.Provider;
import org.apache.abdera.protocol.server.servlet.AbderaServlet;
import org.apache.commons.lang.StringUtils;
import org.atomhopper.abdera.WorkspaceProvider;
import org.atomhopper.config.AtomHopperConfigurationPreprocessor;
import org.atomhopper.config.WorkspaceConfigProcessor;
import org.atomhopper.config.org.atomhopper.config.v2.AtomHopperConfigMarshaller;
import org.atomhopper.config.org.atomhopper.config.v2.AtomHopperConfigurationReader;
import org.atomhopper.config.v1_0.Configuration;
import org.atomhopper.config.v1_0.ConfigurationDefaults;
import org.atomhopper.config.v1_0.HostConfiguration;
import org.atomhopper.config.v1_0.WorkspaceConfiguration;
import org.atomhopper.exceptions.ContextAdapterResolutionException;
import org.atomhopper.exceptions.ServletInitException;
import org.atomhopper.servlet.ApplicationContextAdapter;
import org.atomhopper.servlet.DefaultEmptyContext;
import org.atomhopper.servlet.ServletInitParameter;
import org.atomhopper.util.config.ConfigurationParser;
import org.atomhopper.util.config.ConfigurationParserException;
import org.atomhopper.util.config.jaxb.JAXBConfigurationParser;
import org.atomhopper.util.config.resource.file.FileConfigurationResource;
import org.atomhopper.util.config.resource.uri.URIConfigurationResource;
import org.atomnuke.plugin.InstanceContext;
import org.atomnuke.service.gc.ReclamationHandler;
import org.atomnuke.util.config.ConfigurationException;
import org.atomnuke.util.config.io.ConfigurationManager;
import org.atomnuke.util.config.io.ConfigurationManagerImpl;
import org.atomnuke.util.config.io.file.FileConfigurationManager;
import org.atomnuke.util.config.update.ConfigurationContext;
import org.atomnuke.util.config.update.ConfigurationUpdateManager;
import org.atomnuke.util.config.update.ConfigurationUpdateManagerImpl;
import org.atomnuke.util.config.update.listener.ConfigurationListener;
import org.atomnuke.util.lifecycle.Reclaimable;
import org.atomnuke.util.remote.AtomicCancellationRemote;
import org.atomnuke.util.remote.CancellationRemote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.xml.bind.JAXBException;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

/**
 * This class is the entry point for the atom server application. This servlet is
 * responsible for setting up any required services as well as performing the
 * parsing of the atom server configuration. In addition, the servlet is also
 * responsible for context clean-up using the destroy method. This method should
 * make sure that any resources that have independent thread life-cycles are correctly
 * disposed of.
 */
public final class AtomHopperServlet extends AbderaServlet {

    private static final Logger LOG = LoggerFactory.getLogger(AtomHopperServlet.class);

    private static final String DEFAULT_CONFIGURATION_LOCATION = "/etc/atomhopper/atom-server.cfg.xml";

    private ApplicationContextAdapter applicationContextAdapter;
    private Abdera abderaReference;

    private ConfigurationUpdateManager updateManager;
    private Poller configPoller;

    private final WorkspaceProvider workspaceProvider;

    public AtomHopperServlet() {
        workspaceProvider = new WorkspaceProvider();
    }

    @Override
    public void destroy() {
        configPoller.destroy();
        updateManager.destroy();
    }

    @Override
    public void init() throws ServletException {

        applicationContextAdapter = getContextAdapter();
        applicationContextAdapter.usingServletContext(getServletContext());

        updateManager = new ConfigurationUpdateManagerImpl(new ReclamationHandler() {
            @Override
            public void garbageCollect() {
                //Noop
            }

            @Override
            public CancellationRemote watch(Reclaimable reclaimable) {
                return new AtomicCancellationRemote();
            }

            @Override
            public CancellationRemote watch(InstanceContext<? extends Reclaimable> instanceContext) {
                return new AtomicCancellationRemote();
            }

            @Override
            public void destroy() {
                //Noop
            }
        });

        final RecurringTask task = new RecurringTask() {
            @Override
            public void run() {
                updateManager.update();
            }
        };

        configPoller = new Poller(task, 15000);
        Thread configThread = new Thread(configPoller, "Configuration Poller Thread");

        configThread.start();
        final String configLocation = getConfigurationLocation();
        LOG.info("Reading configuration: " + configLocation);

        abderaReference = getAbdera();

        try {
            AtomHopperConfigMarshaller marshaller = new AtomHopperConfigMarshaller();
            AtomHopperConfigurationReader<Configuration> configReader = new AtomHopperConfigurationReader<Configuration>(new URIConfigurationResource(new URI(configLocation)));
            ConfigurationManager<Configuration> configMgr = new ConfigurationManagerImpl<Configuration>(marshaller, null, configReader);

            ConfigurationContext<Configuration> cfgCtx = updateManager.register("org.atomhopper.cfg.Instance", configMgr);

            cfgCtx.addListener(new ConfigurationListener<Configuration>() {
                @Override
                public void updated(Configuration configuration) throws ConfigurationException {
                    workspaceProvider.setHostConfiguration(configuration.getHost());
                    final String atomhopperUrlPattern = (getServletConfig().getInitParameter("atomhopper-url-pattern") == null) ?
                            "/" : getServletConfig().getInitParameter("atomhopper-url-pattern");

                    workspaceProvider.init(abderaReference, parseDefaults(configuration.getDefaults()));

                    final AtomHopperConfigurationPreprocessor preprocessor = new AtomHopperConfigurationPreprocessor(configuration);
                    configuration = preprocessor.applyDefaults().getConfiguration();

                    ConfigurationDefaults configurationDefaults = configuration.getDefaults();
                    workspaceProvider.init(abderaReference, parseDefaults(configurationDefaults));

                    for (WorkspaceConfiguration workspaceCfg : configuration.getWorkspace()) {
                        final WorkspaceConfigProcessor cfgProcessor = new WorkspaceConfigProcessor(
                                workspaceCfg, applicationContextAdapter,
                                workspaceProvider.getTargetResolver(), atomhopperUrlPattern);

                        workspaceProvider.getWorkspaceManager().addWorkspaces(cfgProcessor.toHandler());
                    }

                }
            });
        } catch (Exception jaxbe) {
            LOG.error("Failed to read configuration");
            throw new ServletInitException(jaxbe.getMessage(), jaxbe);
        }

        super.init();
    }

    protected ApplicationContextAdapter getContextAdapter() throws ContextAdapterResolutionException {
        String adapterClass = getInitParameter(ServletInitParameter.CONTEXT_ADAPTER_CLASS.toString());

        // If no adapter class is set then use the default empty one
        if (StringUtils.isBlank(adapterClass)) {
            adapterClass = DefaultEmptyContext.class.getName();
        }

        try {
            final Object freshAdapter = Class.forName(adapterClass).newInstance();

            if (freshAdapter instanceof ApplicationContextAdapter) {
                return (ApplicationContextAdapter) freshAdapter;
            }
        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);

            throw new ContextAdapterResolutionException(ex.getMessage(), ex);
        }

        throw new ContextAdapterResolutionException("Unknown application context adapter class: " + adapterClass);
    }

    protected String getConfigurationLocation() {
        final String configLocation = getInitParameter(ServletInitParameter.CONFIGURATION_LOCATION.toString());

        return !StringUtils.isBlank(configLocation) ? configLocation : DEFAULT_CONFIGURATION_LOCATION;
    }

    @Override
    protected Provider createProvider() {
        return workspaceProvider;
    }

    private Map<String, String> parseDefaults(ConfigurationDefaults defaults) {
        final Map<String, String> parameterMap = new HashMap<String, String>();

        if (defaults != null && defaults.getAuthor() != null && !StringUtils.isBlank(defaults.getAuthor().getName())) {
            parameterMap.put("author", defaults.getAuthor().getName());
        }

        return parameterMap;
    }
}
