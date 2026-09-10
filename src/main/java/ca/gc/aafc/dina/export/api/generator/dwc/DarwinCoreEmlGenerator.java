package ca.gc.aafc.dina.export.api.generator.dwc;

import java.io.IOException;
import java.nio.file.Path;

import org.springframework.stereotype.Component;

import ca.aafc.eml.generated.eml.Eml;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;

import jakarta.xml.bind.JAXBException;

import lombok.extern.log4j.Log4j2;

@Component
@Log4j2
public class DarwinCoreEmlGenerator {

  public static final String DEFAULT_EML_FILENAME = "eml.xml";

  public void generateEml(Path emlPath, Eml emlDocument) throws IOException {

    try {
      JAXBContext context = JAXBContext.newInstance(Eml.class);
      Marshaller marshaller = context.createMarshaller();
      marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
      marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
      marshaller.setProperty(Marshaller.JAXB_SCHEMA_LOCATION,
       "https://eml.ecoinformatics.org/eml-2.2.0 https://eml.ecoinformatics.org/eml-2.2.0/eml.xsd");
      marshaller.marshal(emlDocument, emlPath.toFile());

    } catch (JAXBException e) {
      log.error("Error processing eml: {}", e.getMessage(), e);
      throw new IOException("Failed to generate eml: " + e.getMessage(), e);
    }
  }
}
