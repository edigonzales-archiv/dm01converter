package ch.so.agi.interlis;

import ch.interlis.ili2c.Ili2cException;
import ch.interlis.iox.IoxException;
import ch.so.agi.interlis.DM01Converter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DM01ConverterTest {
	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void convertTestSuccess() throws IoxException, Ili2cException, IOException {
    	File file = new File("src/test/data/254900.itf");
    	String outputFolderName = tempFolder.newFolder().getAbsolutePath();

    	DM01Converter dm01converter = new DM01Converter();
    	dm01converter.convert(file.getAbsolutePath(), outputFolderName);
    	
    	File outputFile = Paths.get(outputFolderName, "ch_254900.itf").toFile();
    	long expectedSize = 580683;    	
    	assertEquals(expectedSize, outputFile.length());
    }
    
    @Test
    public void fileParsingException() throws IoxException, Ili2cException, IOException {
    	File file = new File("src/test/data/fubar.itf");
    	String outputFolderName = tempFolder.newFolder().getAbsolutePath();

    	try {
        	DM01Converter dm01converter = new DM01Converter();
        	dm01converter.convert(file.getAbsolutePath(), outputFolderName);	
    	} catch(IoxException e) {
			assertTrue(e.getMessage().equals("could not parse file: fubar.itf"));
    	}	
    }
    
    @Test
    public void noBasketException() throws IoxException, Ili2cException, IOException {
    	File file = new File("src/test/data/no_baskets.itf");
    	String outputFolderName = tempFolder.newFolder().getAbsolutePath();

    	try {
        	DM01Converter dm01converter = new DM01Converter();
        	dm01converter.convert(file.getAbsolutePath(), outputFolderName);	
    	} catch(IllegalArgumentException e) {
			assertTrue(e.getMessage().equals("no baskets in transfer-file"));
    	}	
    }
    
    @Test
    public void notValidExtensionException() throws IoxException, Ili2cException, IOException {
    	File file = new File("src/test/data/254900.xtf");
    	String outputFolderName = tempFolder.newFolder().getAbsolutePath();

    	try {
        	DM01Converter dm01converter = new DM01Converter();
        	dm01converter.convert(file.getAbsolutePath(), outputFolderName);	
    	} catch(IllegalArgumentException e) {
			assertTrue(e.getMessage().equals("not a valid file extension: 254900.xtf"));
    	}	
    }
    
    @Test
    public void notExistingModel() throws IoxException, Ili2cException, IOException {
    	File file = new File("src/test/data/254900_not_existing_model.itf");
    	String outputFolderName = tempFolder.newFolder().getAbsolutePath();

    	try {
        	DM01Converter dm01converter = new DM01Converter();
        	dm01converter.convert(file.getAbsolutePath(), outputFolderName);	
    	} catch(Ili2cException e) {
			assertTrue(e.getMessage().equals("DM01AVNOTEXIST: model(s) not found"));
    	}	
    }
    
    @Test
    public void notValidFile() throws IoxException, Ili2cException, IOException {
    	File file = new File("src/test/data/254900_corrupt.itf");
    	String outputFolderName = tempFolder.newFolder().getAbsolutePath();

    	try {
        	DM01Converter dm01converter = new DM01Converter();
        	dm01converter.convert(file.getAbsolutePath(), outputFolderName);	
    	} catch(IoxException e) {
			assertTrue(e.getMessage().equals("line 30: OBJE, PERI or ETAB expected"));
    	}	
    }
    
    @Test
    /*
     * INTERLIS 1 transfer file (not a cantonal DM01) and with
     * no enumerations.
     */
    public void wrongTransferFileModelNoEnumerations() throws IoxException, Ili2cException, IOException {
    	File file = new File("src/test/data/LookUp_ili1_v1.3.itf");
    	String outputFolderName = tempFolder.newFolder().getAbsolutePath();

    	try {
        	DM01Converter dm01converter = new DM01Converter();
        	dm01converter.convert(file.getAbsolutePath(), outputFolderName);	
    	} catch(IllegalArgumentException e) {
			assertTrue(e.getMessage().equals("no enumerations found for: PLZOrtschaft.OrtschaftsName.Sprache"));
    	}	
    }
    
    //TODO
    // e.g. Baulinien?
    public void wrongTransferFileModel() throws IoxException, Ili2cException, IOException {
    	
    }

}
