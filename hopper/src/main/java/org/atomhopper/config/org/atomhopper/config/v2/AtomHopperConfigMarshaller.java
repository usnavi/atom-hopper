package org.atomhopper.config.org.atomhopper.config.v2;

import org.atomhopper.config.v1_0.Configuration;
import org.atomhopper.config.v1_0.ObjectFactory;
import org.atomnuke.util.config.io.marshall.jaxb.JaxbConfigurationMarhsaller;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;

public class AtomHopperConfigMarshaller extends JaxbConfigurationMarhsaller <Configuration>{

    private final static QName _AtomHopperConfig_QNAME = new QName("http://atomhopper.org/atom/hopper-config/v1.0", "atom-hopper-config");

    public AtomHopperConfigMarshaller() throws JAXBException {
        this(JAXBContext.newInstance(ObjectFactory.class));
    }

    private AtomHopperConfigMarshaller(JAXBContext jaxbc) throws JAXBException {
        this(jaxbc.createMarshaller(), jaxbc.createUnmarshaller());
    }

    public AtomHopperConfigMarshaller(Marshaller jaxbMarshaller, Unmarshaller jaxbUnmarshaller) throws JAXBException {
        super(Configuration.class, _AtomHopperConfig_QNAME, jaxbMarshaller, jaxbUnmarshaller );
    }
}
