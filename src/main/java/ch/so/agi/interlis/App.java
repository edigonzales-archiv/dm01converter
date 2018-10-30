package ch.so.agi.interlis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.interlis.ili2c.Ili2cException;
import ch.interlis.iox.IoxException;

public class App {
    public String getGreeting() {
        return "Hello world.";
    }

    public static void main(String[] args) {
        System.out.println(new App().getGreeting());
        Logger log = LoggerFactory.getLogger(App.class);
         
		if (args.length != 2 ) {
	        log.info("Missing parameters");
	        return;
		}
	
		log.info(args[0]);
		log.info(args[1]);
		
		String inputFileName = args[0];
		String outputPath = args[1];

		DM01Converter dm01Converter = new DM01Converter();
		try {
			dm01Converter.convert(inputFileName, outputPath);
		} catch (IoxException | Ili2cException e) {
			e.printStackTrace();
		}
    }
}
