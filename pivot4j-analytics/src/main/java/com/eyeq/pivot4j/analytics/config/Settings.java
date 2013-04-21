package com.eyeq.pivot4j.analytics.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.faces.FacesException;
import javax.faces.bean.ApplicationScoped;
import javax.faces.bean.ManagedBean;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.servlet.ServletContext;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.event.ConfigurationEvent;
import org.apache.commons.configuration.event.ConfigurationListener;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.eyeq.pivot4j.el.ExpressionEvaluator;
import com.eyeq.pivot4j.el.ExpressionEvaluatorFactory;
import com.eyeq.pivot4j.el.ExpressionEvaluatorFactoryImpl;
import com.eyeq.pivot4j.el.freemarker.FreeMarkerExpressionEvaluator;

@ManagedBean(name = "settings", eager = true)
@ApplicationScoped
public class Settings {

	public static final String CONFIG_FILE = "pivot4j.config";

	public static final String APPLICATION_HOME = "pivot4j.home";

	protected Logger logger = LoggerFactory.getLogger(getClass());

	private File applicationHome;

	private HierarchicalConfiguration configuration;

	private String theme;

	private String editorTheme;

	private String resourcePrefix;

	private String viewParameterName;

	private String localeAttributeName;

	@PostConstruct
	protected void initialize() {
		if (logger.isInfoEnabled()) {
			logger.info("Reading configuration parameters.");
		}

		FacesContext context = FacesContext.getCurrentInstance();
		ExternalContext externalContext = context.getExternalContext();

		String path = StringUtils.trimToNull(externalContext
				.getInitParameter(APPLICATION_HOME));
		if (path == null) {
			if (logger.isInfoEnabled()) {
				logger.info("Parameter 'applicationHome' is not set. Using the default path.");
			}

			path = System.getProperty("user.home") + File.separator
					+ ".pivot4j";
		} else if (path.endsWith(File.separator)) {
			path = path.substring(0, path.length() - File.separator.length());
		}

		if (logger.isInfoEnabled()) {
			logger.info("Using application home : " + path);
		}

		this.applicationHome = new File(path);

		if (!applicationHome.exists()) {
			applicationHome.mkdirs();
		}

		InputStream in = null;

		try {
			String configPath = StringUtils.trimToNull(externalContext
					.getInitParameter(CONFIG_FILE));
			if (configPath == null) {
				configPath = path + File.separator + "pivot4j-config.xml";

				File configFile = new File(configPath);

				if (!configFile.exists()) {
					String defaultConfig = "/WEB-INF/pivot4j-config.xml";

					if (logger.isInfoEnabled()) {
						logger.info("Config file does not exist. Using default : "
								+ defaultConfig);
					}

					ServletContext servletContext = (ServletContext) externalContext
							.getContext();

					String location = servletContext.getRealPath(defaultConfig);

					if (location != null) {
						configFile = new File(location);
					}
				}

				if (!configFile.exists()) {
					String msg = "Unable to read the default config : "
							+ configFile;
					throw new FacesException(msg);
				}

				in = new FileInputStream(configFile);
			} else {
				URL url;

				if (configPath.startsWith("classpath:")) {
					url = new URL(null, configPath,
							new ClasspathStreamHandler());
				} else {
					url = new URL(configPath);
				}

				in = url.openStream();

				if (in == null) {
					String msg = "Unable to read config from URL : " + url;
					throw new FacesException(msg);
				}
			}

			this.configuration = readConfiguration(context, in);
		} catch (IOException e) {
			String msg = "Failed to read application config : " + e;
			throw new FacesException(msg, e);
		} catch (ConfigurationException e) {
			String msg = "Invalid application config : " + e;
			throw new FacesException(msg, e);
		} finally {
			IOUtils.closeQuietly(in);
		}

		if (logger.isInfoEnabled()) {
			logger.info("Pivot4J Analytics has been initialized successfully.");
		}

		configuration.addConfigurationListener(new ConfigurationListener() {

			@Override
			public void configurationChanged(ConfigurationEvent event) {
				onConfigurationChanged(event);
			}
		});
	}

	/**
	 * @param context
	 * @param in
	 * @return
	 * @throws ConfigurationException
	 * @throws IOException
	 */
	protected HierarchicalConfiguration readConfiguration(FacesContext context,
			InputStream in) throws ConfigurationException, IOException {
		ExpressionEvaluatorFactory factory = new ExpressionEvaluatorFactoryImpl();
		ExpressionEvaluator evaluator = factory
				.getEvaluator(FreeMarkerExpressionEvaluator.NAMESPACE);

		String source = IOUtils.toString(in);
		source = (String) evaluator.evaluate(source, createELContext(context));

		XMLConfiguration configuration = new XMLConfiguration();
		configuration.load(new StringReader(source));

		return configuration;
	}

	/**
	 * @param context
	 * @return
	 */
	protected Map<String, Object> createELContext(FacesContext context) {
		Map<String, Object> elContext = new HashMap<String, Object>();

		elContext.put("FS", File.separator);

		elContext.put("userHome", System.getProperty("user.dir"));
		elContext.put("appHome", applicationHome.getPath());

		ServletContext servletContext = (ServletContext) context
				.getExternalContext().getContext();
		String webRoot = servletContext.getRealPath("/WEB-INF");

		if (webRoot != null) {
			elContext.put("webRoot", webRoot);
		}

		return elContext;
	}

	/**
	 * @param event
	 */
	protected void onConfigurationChanged(ConfigurationEvent event) {
		this.editorTheme = null;

		this.resourcePrefix = null;
		this.viewParameterName = null;
		this.localeAttributeName = null;
	}

	/**
	 * @return the applicationHome
	 */
	public File getApplicationHome() {
		return applicationHome;
	}

	/**
	 * @return the configuration
	 */
	public HierarchicalConfiguration getConfiguration() {
		return configuration;
	}

	public String getTheme() {
		if (theme == null) {
			this.theme = configuration.getString(
					"appearances.ui-theme.default", "aristo").trim();
		}

		return theme;
	}

	public String getEditorTheme() {
		if (editorTheme == null) {
			this.editorTheme = StringUtils.trimToNull(configuration
					.getString("appearances.editor-theme"));
		}

		return editorTheme;
	}

	public String getResourcePrefix() {
		if (resourcePrefix == null) {
			this.resourcePrefix = StringUtils.trimToEmpty(configuration
					.getString("web.resource-prefix"));
		}

		return resourcePrefix;
	}

	public String getViewParameterName() {
		if (viewParameterName == null) {
			this.viewParameterName = configuration.getString(
					"web.view-parameter", "viewId").trim();

			if (viewParameterName == null) {
				this.viewParameterName = "viewId";
			}
		}

		return viewParameterName;
	}

	public String getLocaleAttributeName() {
		if (localeAttributeName == null) {
			this.localeAttributeName = configuration.getString(
					"web.locale-attribute", "locale").trim();
		}

		return localeAttributeName;
	}

	static class ClasspathStreamHandler extends URLStreamHandler {

		/**
		 * @see java.net.URLStreamHandler#openConnection(java.net.URL)
		 */
		@Override
		protected URLConnection openConnection(URL u) throws IOException {
			URL resourceUrl = getClass().getClassLoader().getResource(
					u.getPath());
			if (resourceUrl == null) {
				return null;
			}

			return resourceUrl.openConnection();
		}
	}
}
