package ch.so.agi.interlis;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
	private HashMap<String, HashMap<Integer, Integer>> enumerationMappings = null;
	private ArrayList inputTables = null;
	private ArrayList outputTables = null;
	private EnumCodeMapper enumCodeMapper = new EnumCodeMapper();

	public DM01Converter() {}
	
	/**
	 * Converts surveying data from a cantonal INTERLIS model into the federal model. 
	 * A simple prefix ('ch_') will be added the to file name.
     * LV95 and german only at the moment.
	 * 
	 * @param inputFileName The file to convert
	 * @param outputPath The output path
	 * @throws IoxException
	 * @throws Ili2cException
	 */
	public void convert(String inputFileName, String outputPath) throws IoxException, Ili2cException, IllegalArgumentException {
		convert(inputFileName, outputPath, "de");
	}
	
	/*
	 * Since there is no language support at the moment, the method
	 * is private.
	 */
	private void convert(String inputFileName, String outputPath, String language) throws IoxException, Ili2cException, IllegalArgumentException {
		inputModelName = getModelNameFromTransferFile(inputFileName);		
		iliTdInput = getTransferDescription(inputModelName);
		tag2type = ch.interlis.iom_j.itf.ModelUtilities.getTagMap(iliTdInput);
		inputEnumerations = getEnumerations(iliTdInput);
		
		iliTdOutput = this.getTransferDescription(outputModelName);
		outputEnumerations = getEnumerations(iliTdOutput);

		enumerationMappings = mapEnumerationTypes(inputEnumerations, outputEnumerations);

		try {
			ioxReader = new ch.interlis.iom_j.itf.ItfReader(new File(inputFileName));
			((ItfReader) ioxReader).setModel(iliTdInput);
			((ItfReader) ioxReader).setRenumberTids(false);
			((ItfReader) ioxReader).setReadEnumValAsItfCode(true);

            String outputFileName = Paths.get(outputPath, PREFIX + new File(inputFileName).getName()).toString();
            
			File outputFile = new File(outputFileName);
			ioxWriter = new ItfWriter(outputFile, iliTdOutput);
			ioxWriter.write(new ch.interlis.iox_j.StartTransferEvent("Interlis", "DM01 Interlis Converter", null));                        

			int topicNr = 0;
			
			IoxEvent event = ioxReader.read();
			while (event!=null) {

				if(event instanceof StartBasketEvent) {
					StartBasketEvent basket = (StartBasketEvent) event;

					String basketName = basket.getType();   
					int firstPoint = basketName.indexOf(".");
					String outputClassName = outputModelName + basketName.substring(firstPoint);

					topicNr++;
					StartBasketEvent outputStartBasketEvent = new ch.interlis.iox_j.StartBasketEvent(outputClassName, Integer.toString(topicNr));

					inputTables = ch.interlis.iom_j.itf.ModelUtilities.getItfTables(iliTdInput, basket.getType().split("\\.")[0], basket.getType().split("\\.")[1]);                                        
					outputTables = ch.interlis.iom_j.itf.ModelUtilities.getItfTables(iliTdOutput, outputStartBasketEvent.getType().split("\\.")[0], outputStartBasketEvent.getType().split("\\.")[1]);

					// outputTables is null if input topic does not exist in output model.
					if (outputTables != null) {
						ioxWriter.write(outputStartBasketEvent); 
					} 
				} 
				else if(event instanceof ObjectEvent) {
					if (outputTables != null) {
						IomObject iomObj = ((ObjectEvent)event).getIomObject();
						String tag = iomObj.getobjecttag();
						String tableName = tag.substring(tag.indexOf(".")+1);                                   

						if (this.isOutputTable(tableName) == true) {

							// Map cantonal enumeration to federal enumeration.
							changeEnumeration(iomObj);

							String tagOutput = outputModelName + "." + tableName;
							iomObj.setobjecttag(tagOutput);                                         

							try {
								ioxWriter.write(new ch.interlis.iox_j.ObjectEvent(iomObj));
							} catch (IoxException ioxe) {
								ioxe.printStackTrace();
							}
						} else {
							// do nothing...
							// Table does not exist (but Topic does).
						}
					}
				} 
				else if(event instanceof EndBasketEvent) {
					// Since the event is from the input model, we are not
					// allowed to write it if the topic does not exist.
					if (outputTables != null) {
						ioxWriter.write(new ch.interlis.iox_j.EndBasketEvent());
					}
				} else if(event instanceof EndTransferEvent) {
					ioxReader.close();    
					ioxWriter.write(new ch.interlis.iox_j.EndTransferEvent());
					ioxWriter.flush();
					ioxWriter.close();                                              
					break;
				}
				event = ioxReader.read();
			}  
		} catch (IoxException e) {
			log.error(e.getMessage());
			e.printStackTrace();
			throw new IoxException(e.getMessage());
			// throw gretl exception
		}
	}
	
	/*
	 * Checks if a table from the input model is also in 
	 * the federal model.
	 */
	private boolean isOutputTable(String tableName) {
		for (int i = 0; i < outputTables.size(); i++) {
			Object o = (Object) outputTables.get(i);
			if (o instanceof Table) {
				String scopedOutputTableName = ((Table) o).getScopedName(null);
				String outputTableName = scopedOutputTableName.substring(scopedOutputTableName.indexOf(".")+1);
				if (tableName.equals(outputTableName)) {
					return true;
				}
			} else if (o instanceof LocalAttribute) {
				String scopedOutputTableName = ((LocalAttribute) o).getContainer().getScopedName(null);
				String attrName = ((LocalAttribute) o).getName();
				String outputTableName = scopedOutputTableName.substring(scopedOutputTableName.indexOf(".")+1) + "_" + attrName;
				if (tableName.equals(outputTableName)) {
					return true;
				}                               
			}
		}
		return false;
	}
	
	/*
	 * Maps a cantonal enumeration value to the federal enumeration value.
	 */
	private void changeEnumeration(IomObject iomObj) {
		Object tableObj = tag2type.get(iomObj.getobjecttag());
		
		if (tableObj instanceof AbstractClassDef) {
			AbstractClassDef tableDef = (AbstractClassDef) tag2type.get(iomObj.getobjecttag());
			ArrayList attrs = ch.interlis.iom_j.itf.ModelUtilities.getIli1AttrList(tableDef);
			
			Iterator attri = attrs.iterator(); 
			while (attri.hasNext()) { 
				ViewableTransferElement obj = (ViewableTransferElement)attri.next();

				if (obj.obj instanceof AttributeDef) {
					AttributeDef attr = (AttributeDef) obj.obj;
					Type type = attr.getDomainResolvingAliases();                          
					String attrName = attr.getName();

					if (type instanceof EnumerationType) {
						String tag = iomObj.getobjecttag();
						String keyName = tag.substring(tag.indexOf(".")+1) + "." + attrName;
						
						HashMap enumerationMap = enumerationMappings.get(keyName);
						String attrValue = iomObj.getattrvalue(attrName);
						if (attrValue != null) {  
							try {
								String Ctcode = String.valueOf(enumerationMap.get(Integer.parseInt(attrValue)));
								iomObj.setattrvalue(attrName, Ctcode);
							} catch (java.lang.NullPointerException e) {
								// Enum not found. We assume that this attribute does not exist in federal table.
								// Needed for GL.
							}
						}
					}
				}
			}
		}
	}
	
	/*
	 * Creates the enumeration mapping hash map. 
	 */
	private HashMap<String, HashMap<Integer, Integer>> mapEnumerationTypes(HashMap inputEnumerations, HashMap outputEnumerations ) {
		HashMap mappings = new HashMap();

		Set keys = outputEnumerations.keySet();
		for (Iterator it = keys.iterator(); it.hasNext();) {
			String key = (String) it.next();
						
			EnumerationType cantonalEnumType = (EnumerationType) inputEnumerations.get(key);
			if (cantonalEnumType == null) {
				// Possible LineAttrib enumeration with a different counter (LineAttribXX).
				// It will check if the first part of the fedral enum name and the last part can
				// be found in the cantonal map with all enums.
				// TODO: But I don't think that this works really properly in all cases. 
				// (Since it is not really qualified)
				// Needed for GL.
				String[] parts = key.split("\\.");
				String firstPart = parts[0];
				String lastPart = parts[parts.length-1];

				Set inputKeys = inputEnumerations.keySet();
				for (Iterator jt = inputKeys.iterator(); jt.hasNext();) {
					String inputKey = (String)jt.next();
					if (inputKey.contains(firstPart) && inputKey.contains(lastPart)) {
						cantonalEnumType = (EnumerationType) inputEnumerations.get(inputKey);
					}
				}
				if (cantonalEnumType == null) {
					throw new IllegalArgumentException("no enumerations found for: " + key);

				}
			}
			EnumerationType federalEnumType = (EnumerationType) outputEnumerations.get(key);

			HashMap mapping = new HashMap();

			ArrayList cantonalElementList = new ArrayList();
			ch.interlis.iom_j.itf.ModelUtilities.buildEnumList(cantonalElementList, "", cantonalEnumType.getConsolidatedEnumeration());
			
			ArrayList federalElementList = new ArrayList();
			ch.interlis.iom_j.itf.ModelUtilities.buildEnumList(federalElementList, "", federalEnumType.getConsolidatedEnumeration());

			Iterator iter = cantonalElementList.iterator();
			int k = 0;
			while(iter.hasNext()){
				String cantonalCode = (String)iter.next();

				if (federalElementList.contains(cantonalCode)) {
					mapping.put(k, federalElementList.indexOf(cantonalCode));

				} else {
					String[] values = cantonalCode.split("\\.");
					String cantonalCodeTmp = "";
					for (int i = values.length-1; i != 0; i--) {
						for (int j = 0; j < i; j++) {
							cantonalCodeTmp = cantonalCodeTmp + "." + values[j];
						}
						cantonalCodeTmp = cantonalCodeTmp.substring(1);
						if (federalElementList.contains(cantonalCodeTmp)) {
							mapping.put(k, federalElementList.indexOf(cantonalCodeTmp));
							break;
						}
					}
				}
				k++;
			}
			mappings.put(key, mapping);
		}
		return mappings;
	}       

	/*
	 * Get all enumerations from a model / transfer description.
	 */
	private HashMap<String, EnumerationType> getEnumerations(ch.interlis.ili2c.metamodel.TransferDescription iliTd) {
		HashMap<String,EnumerationType> enumMap = new HashMap<String,EnumerationType>();

		Iterator modeli = iliTd.iterator();
		while (modeli.hasNext()) {
			Object mObj = modeli.next();

			if (mObj instanceof Model) {
				Model model = (Model) mObj;
				if (model instanceof TypeModel) {
					continue;                               
				}
				if (model instanceof PredefinedModel) {
					continue;
				}                                       

				Iterator<Element> topici = model.iterator();
				while (topici.hasNext()) {
					Object tObj = topici.next();

					// Unnoetig, da jede Enumeration einmal fuer ein
					// Attribut verwendet wird. Und falls nicht, ist
					// sie nicht von Interesse.
					if (tObj instanceof Domain) {
						Type domainType = ((Domain) tObj).getType();

						if (domainType instanceof EnumerationType) {
							Enumeration enumeration = ((EnumerationType) domainType).getEnumeration();
						}                                                        
					}
					else if (tObj instanceof Topic) {
						Topic topic = (Topic) tObj;
						Iterator iter = topic.iterator();

						while (iter.hasNext()) {
							Object obj = iter.next();

							// Unnoetig, da jede Enumeration einmal fuer ein
							// Attribut verwendet wird. Und falls nicht, ist
							// sie nicht von Interesse.
							if (obj instanceof Domain) {
								Domain domain = (Domain) obj;
								Type domainType = domain.getType();     

								if (domainType instanceof EnumerationType) {
									Enumeration enumeration = ((EnumerationType) domainType).getEnumeration();
								}

							// Eigentlich nur das hier noetig.
							// Siehe oben.
							} else if (obj instanceof Viewable) {
								Viewable v = (Viewable) obj;

								if(ch.interlis.iom_j.itf.ModelUtilities.isPureRefAssoc(v)){
									continue;
								}

								List attrv = ch.interlis.iom_j.itf.ModelUtilities.getIli1AttrList((AbstractClassDef) v);
								Iterator attri = attrv.iterator();
								while (attri.hasNext()) {       
									ch.interlis.ili2c.metamodel.ViewableTransferElement attrObj = (ch.interlis.ili2c.metamodel.ViewableTransferElement) attri.next();                                                                                
									if(attrObj.obj instanceof AttributeDef) {
										AttributeDef attrdefObj = (AttributeDef) attrObj.obj;
										Type type = attrdefObj.getDomainResolvingAliases();
										String attrName = attrdefObj.getContainer().getScopedName(null) + "." + attrdefObj.getName();
										String keyName = attrName.substring(attrName.indexOf(".")+1);
										if (type instanceof EnumerationType) {
											EnumerationType enumType = (EnumerationType) type;
											enumMap.put(keyName, enumType);                                                                                 
										}
									} 
								}
							}
						}
					} 
				}
			}
		}
		return enumMap;
	}

	private ch.interlis.ili2c.metamodel.TransferDescription getTransferDescription(String iliModelName) throws Ili2cException {
    	IliManager manager = new IliManager();
    	String repositories[] = new String[]{"http://models.interlis.ch/", "http://models.kkgeo.ch/", "http://models.geo.admin.ch/"};
    	manager.setRepositories(repositories);
    	ArrayList modelNames = new ArrayList();
    	modelNames.add(iliModelName);
    	Configuration config = manager.getConfig(modelNames, 1.0);
    	ch.interlis.ili2c.metamodel.TransferDescription iliTd = Ili2c.runCompiler(config);

		if (iliTd == null) {
			throw new IllegalArgumentException("INTERLIS compiler failed"); // TODO: can this be tested?
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
            	throw new IllegalArgumentException("not a valid file extension: " + new File(transferFileName).getName());
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
