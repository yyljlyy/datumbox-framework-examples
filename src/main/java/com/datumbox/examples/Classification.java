/**
 * Copyright (C) 2013-2016 Vasilis Vryniotis <bbriniotis@datumbox.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datumbox.examples;

import com.datumbox.framework.common.Configuration;
import com.datumbox.framework.common.dataobjects.Dataframe;
import com.datumbox.framework.common.dataobjects.Record;
import com.datumbox.framework.common.dataobjects.TypeInference;
import com.datumbox.framework.common.utilities.PHPMethods;
import com.datumbox.framework.common.utilities.RandomGenerator;
import com.datumbox.framework.core.machinelearning.classification.SoftMaxRegression;
import com.datumbox.framework.core.machinelearning.datatransformation.XMinMaxNormalizer;
import com.datumbox.framework.core.machinelearning.featureselection.continuous.PCA;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Classification example.
 * 
 * @author Vasilis Vryniotis <bbriniotis@datumbox.com>
 */
public class Classification {
    
    /**
     * Example of how to use directly the algorithms of the framework in order to
     * perform classification. A similar approach can be used to perform clustering,
     * regression, build recommender system or perform topic modeling and dimensionality
     * reduction.
     * 
     * @param args the command line arguments
     */
    public static void main(String[] args) { 
        /**
         * There are two configuration files in the resources folder:
         * 
         * - datumbox.config.properties: It contains the configuration for the storage engines (required)
         * - logback.xml: It contains the configuration file for the logger (optional)
         */
        
        //Initialization
        //--------------
        RandomGenerator.setGlobalSeed(42L); //optionally set a specific seed for all Random objects
        Configuration conf = Configuration.getConfiguration(); //default configuration based on properties file
        //conf.setDbConfig(new InMemoryConfiguration()); //use In-Memory storage (default)
        //conf.setDbConfig(new MapDBConfiguration()); //use MapDB storage
        //conf.getConcurrencyConfig().setParallelized(true); //turn on/off the parallelization
        //conf.getConcurrencyConfig().setMaxNumberOfThreadsPerTask(4); //set the concurrency level
        
        
        
        
        //Reading Data
        //------------
        Dataframe trainingDataframe;
        try (Reader fileReader = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(Paths.get(Classification.class.getClassLoader().getResource("datasets/diabetes/diabetes.tsv.gz").toURI()).toFile())), "UTF-8"))) {
            LinkedHashMap<String, TypeInference.DataType> headerDataTypes = new LinkedHashMap<>();
            headerDataTypes.put("pregnancies", TypeInference.DataType.NUMERICAL);
            headerDataTypes.put("plasma glucose", TypeInference.DataType.NUMERICAL);
            headerDataTypes.put("blood pressure", TypeInference.DataType.NUMERICAL);
            headerDataTypes.put("triceps thickness", TypeInference.DataType.NUMERICAL);
            headerDataTypes.put("serum insulin", TypeInference.DataType.NUMERICAL);
            headerDataTypes.put("bmi", TypeInference.DataType.NUMERICAL);
            headerDataTypes.put("dpf", TypeInference.DataType.NUMERICAL);
            headerDataTypes.put("age", TypeInference.DataType.NUMERICAL);
            headerDataTypes.put("test result", TypeInference.DataType.CATEGORICAL);

            trainingDataframe = Dataframe.Builder.parseCSVFile(fileReader, "test result", headerDataTypes, '\t', '"', "\r\n", null, null, conf);
        }
        catch(UncheckedIOException | IOException | URISyntaxException ex) {
            throw new RuntimeException(ex);
        }
        Dataframe testingDataframe = trainingDataframe.copy();
        
        
        //Transform Dataframe
        //-----------------
        
        //Normalize continuous variables
        XMinMaxNormalizer dataTransformer = new XMinMaxNormalizer("Diabetes", conf);
        dataTransformer.fit_transform(trainingDataframe, new XMinMaxNormalizer.TrainingParameters());
        


        //Feature Selection
        //-----------------
        
        //Perform dimensionality reduction using PCA
        
        PCA featureSelection = new PCA("Diabetes", conf);
        PCA.TrainingParameters featureSelectionParameters = new PCA.TrainingParameters();
        featureSelectionParameters.setMaxDimensions(trainingDataframe.xColumnSize()-1); //remove one dimension
        featureSelectionParameters.setWhitened(false);
        featureSelectionParameters.setVariancePercentageThreshold(0.99999995);
        featureSelection.fit_transform(trainingDataframe, featureSelectionParameters);
        
        
        
        //Fit the classifier
        //------------------
        
        SoftMaxRegression classifier = new SoftMaxRegression("Diabetes", conf);
        
        SoftMaxRegression.TrainingParameters param = new SoftMaxRegression.TrainingParameters();
        param.setTotalIterations(200);
        param.setLearningRate(0.1);
        
        classifier.fit(trainingDataframe, param);
        
        //Denormalize trainingDataframe (optional)
        dataTransformer.denormalize(trainingDataframe);
        
        
        //Use the classifier
        //------------------
        
        //Apply the same data transformations on testingDataframe 
        dataTransformer.transform(testingDataframe);
        
        //Apply the same featureSelection transformations on testingDataframe
        featureSelection.transform(testingDataframe);
        
        //Get validation metrics on the training set
        SoftMaxRegression.ValidationMetrics vm = classifier.validate(testingDataframe);
        classifier.setValidationMetrics(vm); //store them in the model for future reference
        
        //Denormalize testingDataframe (optional)
        dataTransformer.denormalize(testingDataframe);
        
        System.out.println("Results:");
        for(Map.Entry<Integer, Record> entry: testingDataframe.entries()) {
            Integer rId = entry.getKey();
            Record r = entry.getValue();
            System.out.println("Record "+rId+" - Real Y: "+r.getY()+", Predicted Y: "+r.getYPredicted());
        }
        
        System.out.println("Classifier Statistics: "+PHPMethods.var_export(vm));
        
        
        
        //Clean up
        //--------
        
        //Delete data transformer, featureselector and classifier.
        dataTransformer.delete();
        featureSelection.delete();
        classifier.delete();
        
        //Delete Dataframes.
        trainingDataframe.delete();
        testingDataframe.delete();
    }
    
}
