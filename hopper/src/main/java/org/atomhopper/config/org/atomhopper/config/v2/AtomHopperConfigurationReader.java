package org.atomhopper.config.org.atomhopper.config.v2;

import org.atomhopper.util.config.resource.ConfigurationResource;
import org.atomnuke.util.config.ConfigurationException;
import org.atomnuke.util.config.io.ConfigurationReader;
import org.atomnuke.util.config.io.UpdateTag;
import org.atomnuke.util.config.io.marshall.ConfigurationMarshaller;

import java.io.IOException;

public class AtomHopperConfigurationReader<T> implements ConfigurationReader<T> {

    private final ConfigurationResource configResource;

    public AtomHopperConfigurationReader(ConfigurationResource configResource) {
        this.configResource = configResource;
    }

    @Override
    public UpdateTag readUpdateTag() throws ConfigurationException {
        return new UpdateTag() {
            @Override
            public long tagValue() {
                return 0;
            }
        };
    }

    @Override
    public T read(ConfigurationMarshaller<T> tConfigurationMarshaller) throws ConfigurationException {
        try {
            return tConfigurationMarshaller.unmarhsall(configResource.getInputStream());
        } catch (IOException e) {
            throw new ConfigurationException(e);
        }
    }
}
