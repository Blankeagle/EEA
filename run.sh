#mvn exec:java -Dexec.mainClass="edu.cmu.cs.lti.cds.PreparationPipeline" -Dexec.args="/home/hector/projects/gigascript_exp/data/giga_ny/ /home/hector/projects/uima-base-tools/fanse-parser/src/main/resources/"
mvn exec:java -Dexec.mainClass="edu.cmu.cs.lti.cds.runners.StreamingEntityClusteringRunner" 
