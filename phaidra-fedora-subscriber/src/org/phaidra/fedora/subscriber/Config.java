package org.phaidra.fedora.subscriber;


import java.io.File;

import java.io.InputStream;

import java.net.URL;
import java.util.Enumeration;
import java.util.Hashtable;

import java.util.Properties;
import java.util.StringTokenizer;


import org.apache.log4j.Logger;

import org.phaidra.fedora.subscriber.errors.ConfigException;

import org.fcrepo.client.FedoraClient;

import org.fcrepo.server.access.FedoraAPIA;
import org.fcrepo.server.management.FedoraAPIM;

/**
 * Reads and checks the configuration files,
 * sets and gets the properties,
 * generates index-specific operationsImpl object.
 * 
 * A Config object may exist for each given configuration.
 * The normal situation is that the default currentConfig is named
 * by finalConfigName = "pfsconfigFinal" ( pre 2.3 = 'config').
 * 
 * For test purposes, the configure operation may be called with the configName
 * matching other given configurations, and then the configure operation
 * with a property may be used to change property values for test purposes.
 * 
 * @author 
 * @version
 */
public class Config {

    FedoraClient fedoraClient = null;
    
    private static Config currentConfig = null;
    
    private static Hashtable<String, Config> configs = new Hashtable<String, Config>();
    
    private static String finalConfigName = "pfsconfigFinal";
    
    private String configName = null;
    
    private Properties fcoProps = null;
    
    private Properties pfsProps = null;
    
    private Hashtable<String, Properties> repositoryNameToProps = null;
    
    private String defaultRepositoryName = null;
    
    private Hashtable<String, Properties> updaterNameToProps = null;

    private StringBuffer errors = null;
    
    private final Logger logger = Logger.getLogger(Config.class);

    /**
     * The configure operation creates a new current Config object.
     */
    public static void configure(String configNameIn) throws ConfigException {
    	String configName = configNameIn;
    	if (configName==null || configName.equals(""))
    		configName = finalConfigName;
        currentConfig = new Config(configName);
        configs.put(configName, currentConfig);
    }

   

    /**
     * The configure operation with a property 
     * - creates a new Config object with the configName, if it does not exist,
     * - and sets that property, if it does not give error.
     */
    public static void configure(String configName, String propertyName, String propertyValue) throws ConfigException {
    	Config config = configs.get(configName);
    	if (config==null) {
    		config = new Config(configName);
            configs.put(configName, config);
    	}
    	String beforeValue = config.getProperty(propertyName);
    	config.setProperty(propertyName, propertyValue);
    	config.errors = new StringBuffer();
    	try {
			config.checkConfig();
		} catch (ConfigException e) {
	    	config.setProperty(propertyName, beforeValue);
    		throw new ConfigException(config.errors.toString());
		}
    }
    
    public static Config getCurrentConfig() throws ConfigException {
        if (currentConfig == null)
            currentConfig = new Config(finalConfigName);
        return currentConfig;
    }
    
    public static Config getConfig(String configName) throws ConfigException {
    	Config config = configs.get(configName);
        if (config == null) {
        	config = new Config(configName);
            configs.put(configName, config);
        }
        return config;
    }
    
    public Config() {
    }
    
    public Config(String configNameIn) throws ConfigException {
    	configName = configNameIn;
    	if (configName==null || configName.equals(""))
    		configName = finalConfigName;
        errors = new StringBuffer();
        
//      Get pfs properties
    	pfsProps = getPfsConfigProps("/"+configName+"/pfs.properties");

//      Get updater properties
    	String updaterProperty = getProperty(pfsProps, "pfs.updaterNames");
    	if(updaterProperty == null) {
    		updaterNameToProps = null; // No updaters will be created
    	} else {           
    		updaterNameToProps = new Hashtable<String, Properties>();
    		StringTokenizer updaterNames = new StringTokenizer(updaterProperty);
    		while (updaterNames.hasMoreTokens()) {
    			String updaterName = updaterNames.nextToken();
				try {
					updaterNameToProps.put(updaterName, getPfsConfigProps("/"+configName+"/updater/"+updaterName+"/updater.properties"));
				} catch (Exception e) {
	            	errors.append("\n*** " + e.toString());
				}
    		}
    	}
    	
//      Get repository properties
        repositoryNameToProps = new Hashtable<String, Properties>();
        defaultRepositoryName = null;
        StringTokenizer repositoryNames = new StringTokenizer(getProperty(pfsProps, "pfs.repositoryNames"));
        while (repositoryNames.hasMoreTokens()) {
            String repositoryName = repositoryNames.nextToken();
            if (defaultRepositoryName == null)
                defaultRepositoryName = repositoryName;
            try {
				repositoryNameToProps.put(repositoryName, getPfsConfigProps("/"+configName+"/repository/"+repositoryName+"/repository.properties"));
			} catch (Exception e) {
            	errors.append("\n*** " + e.toString());
			}
        }

    }
  
    private void checkConfig() throws ConfigException {
        
//  	Check for unknown properties, indicating typos or wrong property names
    	String[] propNames = { 			
    			"pfs.repositoryNames",
    			"pfs.updaterNames"
    	};
    	checkPropNames("pfs.properties", pfsProps, propNames);


//		Check updater properties
    	Enumeration<String> updaterNames = updaterNameToProps.keys();
    	while (updaterNames.hasMoreElements()) {
    		String updaterName = updaterNames.nextElement();
    		Properties props = updaterNameToProps.get(updaterName);  
			String updaterFilePath = configName+"/updater/"+updaterName+"/updater.properties";
			if(getProperty(props, "java.naming.factory.initial") == null) {
				errors.append("\n*** java.naming.factory.initial not provided in "+updaterFilePath);
			}
			if(getProperty(props, "java.naming.provider.url") == null) {
				errors.append("\n*** java.naming.provider.url not provided in "+updaterFilePath);
			}
			if(getProperty(props, "connection.factory.name") == null) {
				errors.append("\n*** connection.factory.name not provided in "+updaterFilePath);
			}
			if(getProperty(props, "client.id") == null) {
				errors.append("\n*** client.id not provided in "+updaterFilePath);
			}  
    	}
    	


//  	Check repository properties
    	Enumeration<String> repositoryNames = repositoryNameToProps.keys();
    	while (repositoryNames.hasMoreElements()) {
    		String repositoryName = repositoryNames.nextElement();
    		Properties props = repositoryNameToProps.get(repositoryName);    		
//  		Check for unknown properties, indicating typos or wrong property names
    		String[] reposPropNames = {
    				"pfsrepository.repositoryName",
    				"pfsrepository.fedoraSoap",
    				"pfsrepository.fedoraUser",
    				"pfsrepository.fedoraPass",
    				"pfsrepository.fedoraObjectDir",
    				"pfsrepository.fedoraVersion",
    				"pfsrepository.trustStorePath",
    				"pfsrepository.trustStorePass"
    		};
    		checkPropNames(configName+"/repository/"+repositoryName+"/repository.properties", props, reposPropNames);

//  		Check repositoryName
    		String propsRepositoryName = getProperty(props, "pfsrepository.repositoryName");
    		if (!repositoryName.equals(propsRepositoryName)) {
    			errors.append("\n*** "+configName+"/repository/" + repositoryName +
    					": pfsrepository.repositoryName must be=" + repositoryName);
    		}
    	
    	}
        if (logger.isDebugEnabled())
            logger.debug("configCheck configName="+configName+" errors="+errors.toString());
    	if (errors.length()>0)
    		throw new ConfigException(errors.toString());
    }

    
    private void checkPropNames(String propsFileName, Properties props, String[] propNames) {
//		Check for unknown properties, indicating typos or wrong property names
        Enumeration<Object> it = props.keys();
        while (it.hasMoreElements()) {
        	String propName = (String)it.nextElement();
        	for (int i=0; i<propNames.length; i++) {
        		if (propNames[i].equals(propName)) {
        			propName = null;
        		}
        	}
        	if (propName!=null) {
                errors.append("\n*** unknown config property in "+propsFileName+": " + propName);
        	}
        }
    }
    
    public String getConfigName() {
        return configName;
    }
    
    public String getSoapBase() {
        return getProperty(pfsProps, "pfs.soapBase");
    }
    
    public String getSoapUser() {
        return getProperty(pfsProps, "pfs.soapUser");
    }
    
    public String getSoapPass() {
        return getProperty(pfsProps, "pfs.soapPass");
    }

    
    public int getWriteLimit() {
    	int writeLimit = 100000; // the Tika default value
		try {
			writeLimit = Integer.parseInt(getProperty(pfsProps, "pfs.writeLimit"));
		} catch (NumberFormatException e) {
		}
    	return writeLimit;
    }

    
    public String getIndexNames(String indexNames) {
        if (indexNames==null || indexNames.equals("")) 
            return getProperty(pfsProps, "pfs.indexNames");
        else 
            return indexNames;
    }
    
    public String getRepositoryName(String repositoryName) {
        if (repositoryName==null || repositoryName.equals("")) 
            return defaultRepositoryName;
        else 
            return repositoryName;
    }
    
    public String getRepositoryNameFromUrl(URL url) {
    	String repositoryName = "";
    	String hostPort = url.getHost();
    	if (url.getPort()>-1)
    		hostPort += ":"+url.getPort();
        if (!(hostPort==null || hostPort.equals(""))) {
        	Enumeration<Properties> propss = repositoryNameToProps.elements();
        	while (propss.hasMoreElements()) {
        		Properties props = propss.nextElement();
        		String fedoraSoap = getProperty(props, "pfsrepository.fedoraSoap");
        		if (fedoraSoap != null && fedoraSoap.indexOf(hostPort) > -1) {
        			return getProperty(props, "pfsrepository.repositoryName", defaultRepositoryName);
        		}
        	}
        }
        return repositoryName;
    }
    
    public Properties getRepositoryProps(String repositoryName) {
        return (repositoryNameToProps.get(repositoryName));
    }
    
    public String getFedoraSoap(String repositoryName) {
        return (getRepositoryProps(repositoryName)).getProperty("pfsrepository.fedoraSoap");
    }
    
    public String getFedoraUser(String repositoryName) {
        return (getRepositoryProps(repositoryName)).getProperty("pfsrepository.fedoraUser");
    }
    
    public String getFedoraPass(String repositoryName) {
        return (getRepositoryProps(repositoryName)).getProperty("pfsrepository.fedoraPass");
    }
    
    public File getFedoraObjectDir(String repositoryName) throws ConfigException {
        String fedoraObjectDirName = insertSystemProperties(getRepositoryProps(repositoryName).getProperty("pfsrepository.fedoraObjectDir"));
        File fedoraObjectDir = new File(fedoraObjectDirName);
       // if (fedoraObjectDir == null) {
        //    throw new ConfigException(repositoryName+": pfsrepository.fedoraObjectDir=" + fedoraObjectDirName + " not found");
        //}
        return fedoraObjectDir;
    }
    
    public String getFedoraVersion(String repositoryName) {
        return (getRepositoryProps(repositoryName)).getProperty("pfsrepository.fedoraVersion");
    }
    
    public String getTrustStorePath(String repositoryName) {
        return (getRepositoryProps(repositoryName)).getProperty("pfsrepository.trustStorePath");
    }
    
    public String getTrustStorePass(String repositoryName) {
        return (getRepositoryProps(repositoryName)).getProperty("pfsrepository.trustStorePass");
    }

    public Hashtable<String, Properties> getUpdaterProps() {
        return updaterNameToProps;
    }    

    
    private String insertSystemProperties(String propertyValue) {
    	String result = propertyValue;
        if (logger.isDebugEnabled())
            logger.debug("insertSystemProperties propertyValue="+result);
    	while (result != null && result.indexOf("${") > -1) {
            if (logger.isDebugEnabled())
                logger.debug("insertSystemProperties propertyValue="+result);
    		result = insertSystemProperty(result);
            if (logger.isDebugEnabled())
                logger.debug("insertSystemProperties propertyValue="+result);
    	}
    	return result;
    }
    
    private String insertSystemProperty(String propertyValue) {
    	String result = propertyValue;
    	int i = result.indexOf("${");
    	if (i > -1) {
    		int j = result.indexOf("}");
    		if (j > -1) {
        		String systemProperty = result.substring(i+2, j);
        		String systemPropertyValue = System.getProperty(systemProperty, "?NOTFOUND{"+systemProperty+"}");
        		result = result.substring(0, i) + systemPropertyValue + result.substring(j+1);
    		}
    	}
    	return result;
    }
    
    public String getProperty(Properties props, String propertyName) {
    	return getProperty(props, propertyName, null);
    }
    
    public String getProperty(Properties props, String propertyName, String defaultValue) {
    	String propertyValue = null;
        if (!(props==null || propertyName==null || propertyName.length()==0)) {
    		propertyValue = props.getProperty(propertyName);
    		if (propertyValue!=null) {
        		propertyValue = propertyValue.trim();
    		}
        }
        if (propertyValue==null){
        	propertyValue = defaultValue;
        }
    	return propertyValue;
    }
    
    public String getProperty(String propertyName) throws ConfigException {
    	String propertyValue = null;
        if (!(propertyName==null || propertyName.equals(""))) {
            int i = propertyName.indexOf("/");
            String propName = propertyName;
        	Properties props = null;
            if (i>-1) {
                String propsName = propertyName.substring(0, i);
                propName = propertyName.substring(i+1);
                if (logger.isDebugEnabled())
                    logger.debug("propsName=" + propsName + " propName=" + propName);
            	else if (repositoryNameToProps.containsKey(propsName)) {
            		props = repositoryNameToProps.get(propsName);
            	}
            } else {
            	props = pfsProps;
            }
    		propertyValue = getProperty(props, propName);
        }
        if (logger.isDebugEnabled())
            logger.debug("getProperty " + propertyName + "=" + propertyValue);
    	return propertyValue;
    }
    
    private Config setProperty(String propertyName, String propertyValue) throws ConfigException {
        if (logger.isInfoEnabled())
            logger.info("property " + propertyName + "=" + propertyValue);
        if (!(propertyName==null || propertyName.equals(""))) {
            int i = propertyName.indexOf("/");
            String propName = propertyName;
        	Properties props = null;
            if (i>-1) {
                String propsName = propertyName.substring(0, i);
                propName = propertyName.substring(i+1);
                if (logger.isDebugEnabled())
                    logger.debug("propsName=" + propsName + " propName=" + propName);
            	else if (repositoryNameToProps.containsKey(propsName)) {
            		props = repositoryNameToProps.get(propsName);
            	}
            } else {
            	props = pfsProps;
            }
        	if (props!=null && propName!=null && propName.length()>0) {
        		props.setProperty(propName, propertyValue);
        	} else {
                throw new ConfigException("property " + propertyName + " not found");
        	}
        }
        return this;
    }
        
    private Properties getPfsConfigProps(String propFilePath) throws ConfigException {
    	Properties props = null;
        try {
            InputStream propStream = Config.class.getResourceAsStream(propFilePath);
            if (propStream != null) {
            	props = new Properties();
            	props.load(propStream);
            	propStream.close();
                if (logger.isInfoEnabled())
                    logger.info("getPfsConfigProps "+propFilePath+"=" + props.toString());
            } else {
                if (logger.isInfoEnabled())
                    logger.info("getPfsConfigProps "+propFilePath+" not found in classpath");
                throw new ConfigException(
                        "*** getPfsConfigProps "+propFilePath+" not found in classpath");
            }
        } catch (Exception e) {
            if (logger.isInfoEnabled())
                logger.info("getPfsConfigProps "+propFilePath+":\n" + e.toString());
            throw new ConfigException(
                    "*** getPfsConfigProps "+propFilePath+":\n" + e.toString());
        }
        return props;
    }
 
    
    private FedoraClient getFedoraClient() throws ConfigException {
        if (logger.isDebugEnabled())
            logger.debug("getFedoraClient");
        if (fedoraClient != null) {
        	return fedoraClient;
        }
        if (logger.isDebugEnabled())
            logger.debug("getFedoraClient new");
		try {
			fedoraClient = new FedoraClient(
					getProperty(fcoProps, "pfsconfigObjects.fedoraSoap"),
					getProperty(fcoProps, "pfsconfigObjects.fedoraUser"),
					getProperty(fcoProps, "pfsconfigObjects.fedoraPass")
					);
		} catch (Exception e) {
            throw new ConfigException("getFedoraClient exception="+e.toString());
		}
        if (logger.isDebugEnabled())
            logger.debug("getFedoraClient new="+getProperty(fcoProps, "pfsconfigObjects.fedoraSoap"));
    	String trustStorePath = getProperty(fcoProps, "pfsconfigObjects.trustStorePath");
    	String trustStorePass = getProperty(fcoProps, "pfsconfigObjects.trustStorePass");
    	if (trustStorePath!=null && trustStorePath.length()>0)
    		System.setProperty("javax.net.ssl.trustStore", trustStorePath);
    	if (trustStorePass!=null && trustStorePass.length()>0)
    		System.setProperty("javax.net.ssl.trustStorePassword", trustStorePass);
        return fedoraClient;
    }
    
    private FedoraAPIA getAPIA() throws ConfigException {
    	FedoraClient fedoraClient = getFedoraClient();
        if (logger.isDebugEnabled())
            logger.debug("getAPIA getFedoraClient ="+fedoraClient);
        FedoraAPIA apia = null;
		try {
			apia = fedoraClient.getAPIA();
		} catch (Exception e) {
            throw new ConfigException("getAPIA exception="+e.toString());
		}
        return apia;
    }
    
    private FedoraAPIM getAPIM() throws ConfigException {
        FedoraAPIM apim = null;
		try {
			apim = getFedoraClient().getAPIM();
		} catch (Exception e) {
            throw new ConfigException("getAPIM exception="+e.toString());
		}
        return apim;
    }
        
   
    
}
