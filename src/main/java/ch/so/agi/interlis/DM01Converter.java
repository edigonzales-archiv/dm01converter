package ch.so.agi.interlis;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.interlis.ili2c.Ili2c;
import ch.interlis.ili2c.Ili2cException;
import ch.interlis.ili2c.config.Configuration;
import ch.interlis.ili2c.metamodel.*;
import ch.interlis.ilirepository.IliManager;
import ch.interlis.iom.*;
import ch.interlis.iox.*;
import ch.interlis.iom_j.itf.ItfReader;
import ch.interlis.iom_j.itf.ItfWriter;
import ch.interlis.iom_j.itf.EnumCodeMapper;
import ch.interlis.iom_j.itf.ModelUtilities;
import ch.interlis.iox_j.jts.Iox2jts;
import ch.interlis.iox_j.jts.Iox2jtsException;
import ch.interlis.iox_j.jts.Jts2iox;

// LV95 only
// German only
public class DM01Converter {
    Logger log = LoggerFactory.getLogger(DM01Converter.class);

	private final String PREFIX = "ch_";
	
	private ch.interlis.ili2c.metamodel.TransferDescription iliTdInput = null;
	private ch.interlis.ili2c.metamodel.TransferDescription iliTdOutput = null;
	private Map tag2type = null;
	private String inputModelName = null;
	private final String outputModelName = "DM01AVCH24LV95D";
	private HashMap<String,EnumerationType> inputEnumerations = null;
	private HashMap<String,EnumerationType> outputEnumerations = null;
	private IoxReader ioxReader = null;
	private IoxWriter ioxWriter = null;
	private HashMap<String,HashMap> enumerationMappings = null;
	private ArrayList inputTables = null;
	private ArrayList outputTables = null;
	private EnumCodeMapper enumCodeMapper = new EnumCodeMapper();

	public DM01Converter() {}
	
	public void convert(String inputFileName, String outputPath) throws IoxException, Ili2cException {
		this.convert(inputFileName, outputPath, "de");
	}
	
	public void convert(String inputFileName, String outputPath, String language) throws IoxException, Ili2cException {
		inputModelName = getModelNameFromTransferFile(inputFileName);		
		iliTdInput = getTransferDescription(inputModelName);
		tag2type = ch.interlis.iom_j.itf.ModelUtilities.getTagMap(iliTdInput);
//		inputEnumerations = this.getEnumerations(iliTdInput);


	}
	
	private ch.interlis.ili2c.metamodel.TransferDescription getTransferDescription(String iliFile) throws Ili2cException {
    	IliManager manager = new IliManager();
    	String repositories[] = new String[]{"http://models.interlis.ch/", "http://models.kkgeo.ch/", "http://models.geo.admin.ch/"};
    	manager.setRepositories(repositories);
    	ArrayList modelNames = new ArrayList();
    	modelNames.add(iliFile);
    	Configuration config = manager.getConfig(modelNames, 1.0);
    	ch.interlis.ili2c.metamodel.TransferDescription iliTd = Ili2c.runCompiler(config);

		if (iliTd == null) {
			throw new IllegalArgumentException("INTERLIS compiler failed");
		}
		return iliTd;   
	}

	
	// credits: http://www.eisenhutinformatik.ch/iox-ili/IOX-ILI-Tutorial-20091203-de.pdf	
	private String getModelNameFromTransferFile(String transferFileName) throws IoxException {
        String model = null;
        String ext = FilenameUtils.getExtension(transferFileName);
        IoxReader ioxReader = null;

        try {
            File transferFile = new File(transferFileName);

            if (ext.equalsIgnoreCase("itf")) {
                ioxReader = new ItfReader(transferFile);
            } else {
            	throw new IoxException("not a valid file exentension: " + new File(transferFileName).getName());
            }

            IoxEvent event;
            StartBasketEvent be = null;
            do {
                event = ioxReader.read();
                if (event instanceof StartBasketEvent) {
                    be = (StartBasketEvent) event;
                    break;
                }
            } while (!(event instanceof EndTransferEvent));

            ioxReader.close();
            ioxReader = null;

            if (be == null) {
                throw new IllegalArgumentException("no baskets in transfer-file");
            }

            String namev[] = be.getType().split("\\.");
            model = namev[0];

        } catch (IoxException e) {
            log.error(e.getMessage());
            e.printStackTrace();
            throw new IoxException("could not parse file: " + new File(transferFileName).getName());
        } finally {
            if (ioxReader != null) {
                try {
                    ioxReader.close();
                } catch (IoxException e) {
                    log.error(e.getMessage());
                    e.printStackTrace();
                    throw new IoxException("could not close interlis transfer file: " + new File(transferFileName).getName());
                }
                ioxReader = null;
            }
        }
        return model;
	}
}
