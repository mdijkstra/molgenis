package org.molgenis.generators.db;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.Map;

import org.molgenis.MolgenisOptions;
import org.molgenis.generators.Generator;
import org.molgenis.model.elements.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import freemarker.template.Template;

public class DatabaseConfigGen extends Generator
{
	private static final Logger LOG = LoggerFactory.getLogger(DatabaseConfigGen.class);

	@Override
	public String getDescription()
	{
		return "Generates database configuration that can be used by a Spring container.";
	}

	@Override
	public void generate(Model model, MolgenisOptions options) throws Exception
	{
		if (options.generate_tests)
		{
		}
		else
		{
			File target = new File(this.getSourcePath(options) + APP_DIR.replace('.', '/') + "/DatabaseConfig.java");
			boolean created = target.getParentFile().mkdirs();
			if (!created && !target.getParentFile().exists())
			{
				throw new IOException("could not create " + target.getParentFile());
			}

			Map<String, Object> templateArgs = createTemplateArguments(options);
			templateArgs.put("package", APP_DIR.replace('/', '.'));

			Template template = createTemplate("/" + getClass().getSimpleName() + ".java.ftl");
			OutputStream targetOut = new FileOutputStream(target);
			try
			{
				template.process(templateArgs, new OutputStreamWriter(targetOut, Charset.forName("UTF-8")));
			}
			finally
			{
				targetOut.close();
			}

			LOG.info("generated " + target);
		}
	}
}
