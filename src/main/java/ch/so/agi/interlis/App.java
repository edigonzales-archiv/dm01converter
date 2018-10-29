package ch.so.agi.interlis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    }
}
