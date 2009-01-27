/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.postag;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Iterator;

import opennlp.maxent.DataStream;
import opennlp.maxent.GISModel;
import opennlp.maxent.PlainTextByLineDataStream;
import opennlp.maxent.io.SuffixSensitiveGISModelWriter;
import opennlp.model.AbstractModel;
import opennlp.model.EventStream;
import opennlp.model.TwoPassDataIndexer;
import opennlp.perceptron.SuffixSensitivePerceptronModelWriter;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.ngram.NGramModel;
import opennlp.tools.util.StringList;

public class POSTaggerTrainer {

  private static void usage() {
    System.err.println("Usage: POSTaggerTrainer [-encoding encoding] [-dict dict_file] -model [perceptron,maxnet] training_data model_file_name [cutoff] [iterations]");
    System.err.println("This trains a new model on the specified training file and writes the trained model to the model file.");
    System.err.println("-encoding Specifies the encoding of the training file");
    System.err.println("-dict Specifies that a dictionary file should be created for use in distinguising between rare and non-rare words");
    System.err.println("-model [perceptron|maxent] Specifies what type of model should be used.");
    System.exit(1);
  }

  /**
   *
   * @param samples
   * @param tagDictionary
   * @param ngramDictionary
   * @param beamSize
   * @param cutoff
   * @return
   *
   * @throws IOException  its throws if an {@link IOException} is thrown
   * during IO operations on a temp file which is created during training occur.
   */
  public static POSModel train(Iterator<POSSample> samples, POSDictionary tagDictionary,
      Dictionary ngramDictionary, int cutoff) throws IOException {

   int iterations = 100;

    GISModel posModel = opennlp.maxent.GIS.trainModel(iterations,
        new TwoPassDataIndexer(new POSSampleEventStream(samples,
        new DefaultPOSContextGenerator(ngramDictionary)), cutoff));

    return new POSModel("en", posModel, tagDictionary, ngramDictionary);
  }

  /**
   * Trains a new model.
   *
   * @param evc
   * @param modelFile
   * @throws IOException
   */
  @Deprecated
  public static void trainMaxentModel(EventStream evc, File modelFile) throws IOException {
    AbstractModel model = trainMaxentModel(evc, 100,5);
    new SuffixSensitiveGISModelWriter(model, modelFile).persist();
  }

  /**
   * Trains a new model
   *
   * @param es
   * @param iterations
   * @param cut
   * @return the new model
   * @throws IOException
   */
  @Deprecated
  public static AbstractModel trainMaxentModel(EventStream es, int iterations, int cut) throws IOException {
    return opennlp.maxent.GIS.trainModel(iterations, new TwoPassDataIndexer(es, cut));
  }

  public static AbstractModel trainPerceptronModel(EventStream es, int iterations, int cut) throws IOException {
    return new opennlp.perceptron.PerceptronTrainer().trainModel(iterations, new TwoPassDataIndexer(es, cut), cut);
  }


  public static void main(String[] args) {
    if (args.length == 0){
      usage();
    }
    int ai=0;
    try {
      String encoding = null;
      String dict = null;
      boolean perceptron = false;
      while (args[ai].startsWith("-")) {
        if (args[ai].equals("-encoding")) {
          ai++;
          if (ai < args.length) {
            encoding = args[ai++];
          }
          else {
            usage();
          }
        }
        else if (args[ai].equals("-dict")) {
          ai++;
          if (ai < args.length) {
            dict = args[ai++];
          }
          else {
            usage();
          }
        }
        else if (args[ai].equals("-model")) {
          ai++;
          if (ai < args.length) {
            String type = args[ai++];
            if (type.equals("perceptron")) {
              perceptron = true;
            }
            else if (type.equals("maxent")) {

            }
            else {
              usage();
            }
          }
          else {
            usage();
          }
        }
        else {
          System.err.println("Unknown option "+args[ai]);
          usage();
        }
      }
      File inFile = new File(args[ai++]);
      File outFile = new File(args[ai++]);
      int cutoff = 5;
      int iterations = 100;
      if (args.length > ai) {
        cutoff = Integer.parseInt(args[ai++]);
        iterations = Integer.parseInt(args[ai++]);
      }
      AbstractModel mod;
      if (dict != null) {
        System.err.println("Building dictionary");

        NGramModel ngramModel = new NGramModel();

        DataStream data = new opennlp.maxent.PlainTextByLineDataStream(new java.io.FileReader(inFile));
        while(data.hasNext()) {
          String tagStr = (String) data.nextToken();
          String[] tt = tagStr.split(" ");
          String[] words = new String[tt.length];
          for (int wi=0;wi<words.length;wi++) {
            words[wi] =
                tt[wi].substring(0,tt[wi].lastIndexOf('_'));
          }

          ngramModel.add(new StringList(words), 1, 1);
        }

        System.out.println("Saving the dictionary");

        ngramModel.cutoff(cutoff, Integer.MAX_VALUE);
        Dictionary dictionary = ngramModel.toDictionary(true);

        dictionary.serialize(new FileOutputStream(dict));
      }
      POSSampleEventStream es;
      if (encoding == null) {
        if (dict == null) {
          es = new POSSampleEventStream(new WordTagSampleStream(new PlainTextByLineDataStream(
              new InputStreamReader(new FileInputStream(inFile)))));
        }
        else {
          POSContextGenerator cg = new DefaultPOSContextGenerator(new Dictionary(new FileInputStream(dict)));

          es = new POSSampleEventStream(new WordTagSampleStream(new PlainTextByLineDataStream(
              new InputStreamReader(new FileInputStream(inFile)))),
              cg);
        }
      }
      else {
        if (dict == null) {

          es = new POSSampleEventStream(new WordTagSampleStream(new PlainTextByLineDataStream(
              new InputStreamReader(new FileInputStream(inFile), encoding))));
        }
        else {
          POSContextGenerator cg = new DefaultPOSContextGenerator(new Dictionary(new FileInputStream(dict)));

          es = new POSSampleEventStream(new WordTagSampleStream(new PlainTextByLineDataStream(
              new InputStreamReader(new FileInputStream(inFile), encoding))), cg);
        }
      }
      if (perceptron) {
        mod = trainPerceptronModel(es,iterations, cutoff);
        System.out.println("Saving the model as: " + outFile);
        new SuffixSensitivePerceptronModelWriter(mod, outFile).persist();
      }
      else {
        mod = trainMaxentModel(es, iterations, cutoff);

        System.out.println("Saving the model as: " + outFile);

        new SuffixSensitiveGISModelWriter(mod, outFile).persist();
      }

    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

}