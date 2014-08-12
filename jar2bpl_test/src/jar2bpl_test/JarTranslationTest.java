package jar2bpl_test;

import static org.junit.Assert.fail;

import org.joogie.Dispatcher;
import org.joogie.Options;
import org.junit.Test;

public class JarTranslationTest {

	
	@Test
	public void testJarInputFile() {
		//TODO: design one test case for each sort of input to the translation.
		try {
			String javaFileDir = "regression/jar_input/dacapo-9.12-bach.jar";
			Options.v().setClasspath(javaFileDir);
			String bplFile = "regression/test_output/dacapo-9.12-bach.bpl";

			Dispatcher.run(javaFileDir,
					bplFile);

		} catch (Exception e) {			
			fail("Translation Error " + e.toString());
		}
		
		org.junit.Assert.assertTrue(true);
	}
	

	
	
}



