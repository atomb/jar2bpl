package jar2bpl_test;

import static org.junit.Assert.fail;

import org.joogie.Dispatcher;
import org.joogie.Options;
import org.junit.Test;

import bixie.Bixie;

public class BouncyCastleTranslationTest {

	
	@Test
	public void testJarInputFile() {
		//TODO: design one test case for each sort of input to the translation.
		String bplFile = "regression/test_output/bc.bpl";
		String output_file = "regression/test_output/bc.bpl.report.txt";
		try {
			String javaFileDir = System.getProperty("user.dir")+"/regression/bc/java";
			Options.v().setClasspath(javaFileDir);			
			//Options.v().setSoundThreads(true);
			

			Dispatcher.run(javaFileDir,
					bplFile);
			
		} catch (Exception e) {			
			fail("Translation Error " + e.toString());
		}
		
		Bixie bx = new Bixie();
		bx.run(bplFile, output_file);
		
		org.junit.Assert.assertTrue(true);
	}
	

	
	
}



