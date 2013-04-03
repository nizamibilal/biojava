/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.biojava3.genome;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.logging.Logger;
import org.biojava3.core.sequence.AccessionID;
import org.biojava3.core.sequence.CDSSequence;
import org.biojava3.core.sequence.ChromosomeSequence;
import org.biojava3.core.sequence.DNASequence;
import org.biojava3.core.sequence.ExonSequence;
import org.biojava3.core.sequence.io.FastaReaderHelper;
import org.biojava3.core.sequence.io.FastaWriterHelper;
import org.biojava3.core.sequence.GeneSequence;
import org.biojava3.core.sequence.ProteinSequence;
import org.biojava3.core.sequence.Strand;
import org.biojava3.core.sequence.TranscriptSequence;
import org.biojava3.genome.parsers.gff.Feature;
import org.biojava3.genome.parsers.gff.FeatureI;
import org.biojava3.genome.parsers.gff.FeatureList;
import org.biojava3.genome.parsers.gff.GFF3Reader;
import org.biojava3.genome.parsers.gff.GeneIDGFF2Reader;
import org.biojava3.genome.parsers.gff.GeneMarkGTFReader;

/**
 *
 * @author Scooter Willis <willishf at gmail dot com>
 */
public class GeneFeatureHelper {

    private static final Logger log = Logger.getLogger(GeneFeatureHelper.class.getName());

    
    static public LinkedHashMap<String, ChromosomeSequence> loadFastaAddGeneFeaturesFromUpperCaseExonFastaFile(File fastaSequenceFile,File uppercaseFastaFile, boolean throwExceptionGeneNotFound) throws Exception{
        LinkedHashMap<String, ChromosomeSequence> chromosomeSequenceList = new LinkedHashMap<String, ChromosomeSequence>();
        LinkedHashMap<String, DNASequence> dnaSequenceList = FastaReaderHelper.readFastaDNASequence(fastaSequenceFile);
        for(String accession : dnaSequenceList.keySet()){
            DNASequence contigSequence = dnaSequenceList.get(accession);
            ChromosomeSequence chromsomeSequence = new ChromosomeSequence(contigSequence.getSequenceAsString()); 
            chromsomeSequence.setAccession(contigSequence.getAccession());
            chromosomeSequenceList.put(accession, chromsomeSequence);
        }
        
        
        LinkedHashMap<String, DNASequence> geneSequenceList = FastaReaderHelper.readFastaDNASequence(uppercaseFastaFile);
        for(DNASequence dnaSequence : geneSequenceList.values()){
            String geneSequence = dnaSequence.getSequenceAsString();
            String lcGeneSequence = geneSequence.toLowerCase();
            String reverseGeneSequence = dnaSequence.getReverse().getSequenceAsString();
            String lcReverseGeneSequence = reverseGeneSequence.toLowerCase();
            Integer bioStart = null;
            Integer bioEnd = null;
            Strand strand = Strand.POSITIVE;
            boolean geneFound = false;
            String accession = "";
            DNASequence contigDNASequence = null;
            for(String id : dnaSequenceList.keySet()){
                accession = id;
                contigDNASequence = dnaSequenceList.get(id);
                String contigSequence = contigDNASequence.getSequenceAsString().toLowerCase();
                bioStart = contigSequence.indexOf(lcGeneSequence);
                if(bioStart != -1){
                    bioStart = bioStart + 1;
                    bioEnd = bioStart + geneSequence.length() - 1;
                    geneFound = true;
                    break;
                }else{
                    bioStart = contigSequence.indexOf(lcReverseGeneSequence);
                    if(bioStart != -1){
                        bioStart = bioStart + 1;
                        bioEnd = bioStart - geneSequence.length() - 1;
                        strand = Strand.NEGATIVE;
                        geneFound = true;
                        break;
                    }
                } 
            }
            
            if(geneFound){
                System.out.println("Gene " + dnaSequence.getAccession().toString() + " found at " + contigDNASequence.getAccession().toString() + " " + bioStart + " " + bioEnd + " " + strand);
                ChromosomeSequence chromosomeSequence = chromosomeSequenceList.get(accession);
                
                ArrayList<Integer> exonBoundries = new ArrayList<Integer>();

                //look for transitions from lowercase to upper case
                for(int i = 0; i < geneSequence.length(); i++){
                        if(i == 0 && Character.isUpperCase(geneSequence.charAt(i))){
                            exonBoundries.add(i);
                        }else if(i == geneSequence.length() - 1) {
                            exonBoundries.add(i);
                        }else if(Character.isUpperCase(geneSequence.charAt(i)) && Character.isLowerCase(geneSequence.charAt(i - 1))){
                            exonBoundries.add(i);
                        }else if(Character.isUpperCase(geneSequence.charAt(i)) && Character.isLowerCase(geneSequence.charAt(i + 1))){
                            exonBoundries.add(i);
                        }
                    }
                if(strand == Strand.NEGATIVE){
                     Collections.reverse(exonBoundries);
                }



                GeneSequence geneSeq = chromosomeSequence.addGene(dnaSequence.getAccession(), bioStart, bioEnd, strand);
                geneSeq.setSource(uppercaseFastaFile.getName());
                String transcriptName = dnaSequence.getAccession().getID() + "-transcript";
                TranscriptSequence transcriptSequence = geneSeq.addTranscript(new AccessionID(transcriptName), bioStart, bioEnd);

                int runningFrameLength = 0;
                for(int i = 0; i < exonBoundries.size() - 1; i = i + 2){
                    int cdsBioStart = exonBoundries.get(i) + bioStart;
                    int cdsBioEnd = exonBoundries.get(i + 1) + bioStart;
                    runningFrameLength = runningFrameLength + Math.abs(cdsBioEnd - cdsBioStart) + 1;
                    String  cdsName = transcriptName + "-cds-" + cdsBioStart + "-" + cdsBioEnd;
                    
                    AccessionID cdsAccessionID = new AccessionID(cdsName);
                    ExonSequence exonSequence = geneSeq.addExon(cdsAccessionID, cdsBioStart, cdsBioEnd);
                    int remainder = runningFrameLength % 3;
                    int frame = 0;
                    if(remainder == 1){
                        frame = 2; // borrow 2 from next CDS region
                    }else if(remainder == 2){
                        frame = 1;
                    }
                    CDSSequence cdsSequence = transcriptSequence.addCDS(cdsAccessionID, cdsBioStart, cdsBioEnd, frame);
                }
                
                
            }else{
                if(throwExceptionGeneNotFound){
                    throw new Exception(dnaSequence.getAccession().toString() + " not found");
                }
                System.out.println("Gene not found " + dnaSequence.getAccession().toString());
            }
            
        }
        return chromosomeSequenceList;
    }
    
    /**
     * Output a gff3 feature file that will give the length of each scaffold/chromosome in the fasta file.
     * Used for gbrowse so it knows length.
     * @param fastaSequenceFile
     * @param gffFile
     * @throws Exception
     */
    static public void outputFastaSequenceLengthGFF3(File fastaSequenceFile, File gffFile) throws Exception {
        LinkedHashMap<String, DNASequence> dnaSequenceList = FastaReaderHelper.readFastaDNASequence(fastaSequenceFile);
        String fileName = fastaSequenceFile.getName();
        FileWriter fw = new FileWriter(gffFile);
        fw.write("##gff-version 3\n");
        for (DNASequence dnaSequence : dnaSequenceList.values()) {

            String gff3line = dnaSequence.getAccession().getID() + "\t" + fileName + "\t" + "contig" + "\t" + "1" + "\t" + dnaSequence.getBioEnd() + "\t.\t.\t.\tName=" + dnaSequence.getAccession().getID() + "\n";
            fw.write(gff3line);
        }
        fw.close();
    }

    /**
     * Loads Fasta file and GFF2 feature file generated from the geneid prediction algorithm
     *
     * @param fastaSequenceFile
     * @param gffFile
     * @return
     * @throws Exception
     */
    static public LinkedHashMap<String, ChromosomeSequence> loadFastaAddGeneFeaturesFromGeneIDGFF2(File fastaSequenceFile, File gffFile) throws Exception {
        LinkedHashMap<String, DNASequence> dnaSequenceList = FastaReaderHelper.readFastaDNASequence(fastaSequenceFile);
        LinkedHashMap<String, ChromosomeSequence> chromosomeSequenceList = GeneFeatureHelper.getChromosomeSequenceFromDNASequence(dnaSequenceList);
        FeatureList listGenes = GeneIDGFF2Reader.read(gffFile.getAbsolutePath());
        addGeneIDGFF2GeneFeatures(chromosomeSequenceList, listGenes);
        return chromosomeSequenceList;
    }

    /**
     * Load GFF2 feature file generated from the geneid prediction algorithm and map features onto the chromosome sequences
     *
     * @param chromosomeSequenceList
     * @param listGenes
     * @throws Exception
     */
    static public void addGeneIDGFF2GeneFeatures(LinkedHashMap<String, ChromosomeSequence> chromosomeSequenceList, FeatureList listGenes) throws Exception {
        Collection<String> geneIds = listGenes.attributeValues("gene_id");
        for (String geneid : geneIds) {
            FeatureList gene = listGenes.selectByAttribute("gene_id", geneid);
            FeatureI geneFeature = gene.get(0);
            ChromosomeSequence seq = chromosomeSequenceList.get(geneFeature.seqname());
            geneid = geneid.replaceAll("_", ".G");
            AccessionID geneAccessionID = new AccessionID(geneid);
            GeneSequence geneSequence = null;
            Collection<String> transcriptids = gene.attributeValues("gene_id");
            for (String transcriptid : transcriptids) {
                // get all the individual features (exons, CDS regions, etc.) of this gene
                FeatureList transcriptFeature = listGenes.selectByAttribute("gene_id", transcriptid);
                transcriptid = transcriptid.replaceAll("_", ".G");




                //      String seqName = feature.seqname();
                FeatureI startCodon = null;
                FeatureI stopCodon = null;
                Integer startCodonBegin = null;
                Integer stopCodonEnd = null;
                String startCodonName = "";
                String stopCodonName = "";


                // now select only the coding regions of this gene
                FeatureList firstFeatures = transcriptFeature.selectByType("First");
                FeatureList terminalFeatures = transcriptFeature.selectByType("Terminal");
                FeatureList internalFeatures = transcriptFeature.selectByType("Internal");
                FeatureList singleFeatures = transcriptFeature.selectByType("Single");
                FeatureList cdsFeatures = new FeatureList();
                cdsFeatures.add(firstFeatures);
                cdsFeatures.add(terminalFeatures);
                cdsFeatures.add(internalFeatures);
                cdsFeatures.add(singleFeatures);
                // sort them
                cdsFeatures = cdsFeatures.sortByStart();
                Strand strand = Strand.POSITIVE;
                FeatureI feature = cdsFeatures.get(0);
                if (feature.location().isNegative()) {
                    strand = strand.NEGATIVE;
                }
                if (startCodonBegin == null) {
                    FeatureI firstFeature = cdsFeatures.get(0);
                    if (strand == strand.NEGATIVE) {
                        startCodonBegin = firstFeature.location().bioEnd();
                    } else {
                        startCodonBegin = firstFeature.location().bioStart();
                    }
                }

                if (stopCodonEnd == null) {

                    FeatureI lastFeature = cdsFeatures.get(cdsFeatures.size() - 1);
                    if (strand == strand.NEGATIVE) {
                        stopCodonEnd = lastFeature.location().bioStart();
                    } else {
                        stopCodonEnd = lastFeature.location().bioEnd();
                    }
                }
                //for gtf ordering can be strand based so first is last and last is first
                if (startCodonBegin > stopCodonEnd) {
                    int temp = startCodonBegin;
                    startCodonBegin = stopCodonEnd;
                    stopCodonEnd = temp;
                }

                AccessionID transcriptAccessionID = new AccessionID(transcriptid);
                if (geneSequence == null) {
                    geneSequence = seq.addGene(geneAccessionID, startCodonBegin, stopCodonEnd, strand);
                    geneSequence.setSource(((Feature) feature).source());
                } else {
                    //if multiple transcripts for one gene make sure the gene is defined as the min and max start/end

                    if (startCodonBegin < geneSequence.getBioBegin()) {
                        geneSequence.setBioBegin(startCodonBegin);
                    }
                    if (stopCodonEnd > geneSequence.getBioBegin()) {
                        geneSequence.setBioEnd(stopCodonEnd);
                    }

                }
                TranscriptSequence transcriptSequence = geneSequence.addTranscript(transcriptAccessionID, startCodonBegin, stopCodonEnd);
                if (startCodon != null) {
                    if (startCodonName == null || startCodonName.length() == 0) {
                        startCodonName = transcriptid + "-start_codon-" + startCodon.location().bioStart() + "-" + startCodon.location().bioEnd();
                    }
                    transcriptSequence.addStartCodonSequence(new AccessionID(startCodonName), startCodon.location().bioStart(), startCodon.location().bioEnd());
                }
                if (stopCodon != null) {
                    if (stopCodonName == null || stopCodonName.length() == 0) {
                        stopCodonName = transcriptid + "-stop_codon-" + stopCodon.location().bioStart() + "-" + stopCodon.location().bioEnd();
                    }
                    transcriptSequence.addStopCodonSequence(new AccessionID(stopCodonName), stopCodon.location().bioStart(), stopCodon.location().bioEnd());
                }

                for (FeatureI cdsFeature : cdsFeatures) {
                    Feature cds = (Feature) cdsFeature;
                    String cdsName = cds.getAttribute("transcript_name");
                    if (cdsName == null || cdsName.length() == 0) {
                        cdsName = transcriptid + "-cds-" + cds.location().bioStart() + "-" + cds.location().bioEnd();
                    }
                    AccessionID cdsAccessionID = new AccessionID(cdsName);
                    ExonSequence exonSequence = geneSequence.addExon(cdsAccessionID, cdsFeature.location().bioStart(), cdsFeature.location().bioEnd());
                    CDSSequence cdsSequence = transcriptSequence.addCDS(cdsAccessionID, cdsFeature.location().bioStart(), cdsFeature.location().bioEnd(), cds.frame());
                    cdsSequence.setSequenceScore(cds.score());
                }
            }
        }

    }

    static public LinkedHashMap<String, ChromosomeSequence> getChromosomeSequenceFromDNASequence(LinkedHashMap<String, DNASequence> dnaSequenceList) {
        LinkedHashMap<String, ChromosomeSequence> chromosomeSequenceList = new LinkedHashMap<String, ChromosomeSequence>();
        for (String key : dnaSequenceList.keySet()) {
            DNASequence dnaSequence = dnaSequenceList.get(key);
            ChromosomeSequence chromosomeSequence = new ChromosomeSequence(dnaSequence.getSequenceAsString());
            chromosomeSequence.setAccession(dnaSequence.getAccession());
            chromosomeSequenceList.put(key, chromosomeSequence);
        }
        return chromosomeSequenceList;
    }

    /**
     * Lots of variations in the ontology or descriptors that can be used in GFF3 which requires writing a custom parser to handle a GFF3 generated or used
     * by a specific application. Probably could be abstracted out but for now easier to handle with custom code to deal with gff3 elements that are not
     * included but can be extracted from other data elements.
     * @param fastaSequenceFile
     * @param gffFile
     * @return
     * @throws Exception
     */
    static public LinkedHashMap<String, ChromosomeSequence> loadFastaAddGeneFeaturesFromGmodGFF3(File fastaSequenceFile, File gffFile) throws Exception {
        LinkedHashMap<String, DNASequence> dnaSequenceList = FastaReaderHelper.readFastaDNASequence(fastaSequenceFile);
        LinkedHashMap<String, ChromosomeSequence> chromosomeSequenceList = GeneFeatureHelper.getChromosomeSequenceFromDNASequence(dnaSequenceList);
        FeatureList listGenes = GFF3Reader.read(gffFile.getAbsolutePath());
        addGmodGFF3GeneFeatures(chromosomeSequenceList, listGenes);
        return chromosomeSequenceList;
    }

    static public void addGmodGFF3GeneFeatures(LinkedHashMap<String, ChromosomeSequence> chromosomeSequenceList, FeatureList listGenes) throws Exception {
        FeatureList mRNAFeatures = listGenes.selectByType("mRNA");
        for (FeatureI f : mRNAFeatures) {
            Feature mRNAFeature = (Feature) f;
            String geneid = mRNAFeature.getAttribute("ID");
            String source = mRNAFeature.source();

            FeatureList gene = listGenes.selectByAttribute("Parent", geneid);
            FeatureI geneFeature = gene.get(0);
            ChromosomeSequence seq = chromosomeSequenceList.get(geneFeature.seqname());
            AccessionID geneAccessionID = new AccessionID(geneid);
            GeneSequence geneSequence = null;

            FeatureList cdsFeatures = gene.selectByType("CDS");
            FeatureI feature = cdsFeatures.get(0);
            Strand strand = Strand.POSITIVE;

            if (feature.location().isNegative()) {
                strand = strand.NEGATIVE;
            }
            cdsFeatures = cdsFeatures.sortByStart();







            String seqName = feature.seqname();
            FeatureI startCodon = null;
            FeatureI stopCodon = null;
            Integer startCodonBegin = null;
            Integer stopCodonEnd = null;
            String startCodonName = "";
            String stopCodonName = "";
            FeatureList startCodonList = gene.selectByType("five_prime_UTR");
            if (startCodonList != null && startCodonList.size() > 0) {
                startCodon = startCodonList.get(0);
                if (strand == Strand.NEGATIVE) {
                    startCodonBegin = startCodon.location().bioEnd();
                } else {
                    startCodonBegin = startCodon.location().bioStart();
                }
                startCodonName = startCodon.getAttribute("ID");
            }

            FeatureList stopCodonList = gene.selectByType("three_prime_UTR");

            if (stopCodonList != null && stopCodonList.size() > 0) {
                stopCodon = stopCodonList.get(0);
                if (strand == Strand.NEGATIVE) {
                    stopCodonEnd = stopCodon.location().bioStart();
                } else {
                    stopCodonEnd = stopCodon.location().bioEnd();
                }
                stopCodonName = stopCodon.getAttribute("ID");

            }




            if (startCodonBegin == null) {
                if (strand == Strand.NEGATIVE) {
                    FeatureI firstFeature = cdsFeatures.get(0);

                    startCodonBegin = firstFeature.location().bioEnd();
                } else {
                    FeatureI firstFeature = cdsFeatures.get(0);

                    startCodonBegin = firstFeature.location().bioStart();
                }
            }

            if (stopCodonEnd == null) {
                if (strand == Strand.NEGATIVE) {
                    FeatureI lastFeature = cdsFeatures.get(cdsFeatures.size() - 1);
                    stopCodonEnd = lastFeature.location().bioStart();
                } else {
                    FeatureI lastFeature = cdsFeatures.get(cdsFeatures.size() - 1);
                    stopCodonEnd = lastFeature.location().bioEnd();
                }
            }
            //for gtf ordering can be strand based so first is last and last is first
            if (startCodonBegin > stopCodonEnd) {
                int temp = startCodonBegin;
                startCodonBegin = stopCodonEnd;
                stopCodonEnd = temp;
            }



            AccessionID transcriptAccessionID = new AccessionID(geneid);
            if (geneSequence == null) {
                geneSequence = seq.addGene(geneAccessionID, startCodonBegin, stopCodonEnd, strand);
                geneSequence.setSource(source);
            } else {

                if (startCodonBegin < geneSequence.getBioBegin()) {
                    geneSequence.setBioBegin(startCodonBegin);
                }
                if (stopCodonEnd > geneSequence.getBioBegin()) {
                    geneSequence.setBioEnd(stopCodonEnd);
                }

            }
            TranscriptSequence transcriptSequence = geneSequence.addTranscript(transcriptAccessionID, startCodonBegin, stopCodonEnd);
            if (startCodon != null) {
                if (startCodonName == null || startCodonName.length() == 0) {
                    startCodonName = geneid + "-start_codon-" + startCodon.location().bioStart() + "-" + startCodon.location().bioEnd();
                }
                transcriptSequence.addStartCodonSequence(new AccessionID(startCodonName), startCodon.location().bioStart(), startCodon.location().bioEnd());
            }
            if (stopCodon != null) {
                if (stopCodonName == null || stopCodonName.length() == 0) {
                    stopCodonName = geneid + "-stop_codon-" + stopCodon.location().bioStart() + "-" + stopCodon.location().bioEnd();
                }
                transcriptSequence.addStopCodonSequence(new AccessionID(stopCodonName), stopCodon.location().bioStart(), stopCodon.location().bioEnd());
            }

            for (FeatureI cdsFeature : cdsFeatures) {
                Feature cds = (Feature) cdsFeature;
                String cdsName = cds.getAttribute("ID");
                if (cdsName == null || cdsName.length() == 0) {
                    cdsName = geneid + "-cds-" + cds.location().bioStart() + "-" + cds.location().bioEnd();
                }
                AccessionID cdsAccessionID = new AccessionID(cdsName);
                ExonSequence exonSequence = geneSequence.addExon(cdsAccessionID, cdsFeature.location().bioStart(), cdsFeature.location().bioEnd());
                transcriptSequence.addCDS(cdsAccessionID, cdsFeature.location().bioStart(), cdsFeature.location().bioEnd(), cds.frame());
            }

        }

    }

    static public LinkedHashMap<String, ChromosomeSequence> loadFastaAddGeneFeaturesFromGlimmerGFF3(File fastaSequenceFile, File gffFile) throws Exception {
        LinkedHashMap<String, DNASequence> dnaSequenceList = FastaReaderHelper.readFastaDNASequence(fastaSequenceFile);
        LinkedHashMap<String, ChromosomeSequence> chromosomeSequenceList = GeneFeatureHelper.getChromosomeSequenceFromDNASequence(dnaSequenceList);
        FeatureList listGenes = GFF3Reader.read(gffFile.getAbsolutePath());
        addGlimmerGFF3GeneFeatures(chromosomeSequenceList, listGenes);
        return chromosomeSequenceList;
    }

    static public void addGlimmerGFF3GeneFeatures(LinkedHashMap<String, ChromosomeSequence> chromosomeSequenceList, FeatureList listGenes) throws Exception {
        FeatureList mRNAFeatures = listGenes.selectByType("mRNA");
        for (FeatureI f : mRNAFeatures) {
            Feature mRNAFeature = (Feature) f;
            String geneid = mRNAFeature.getAttribute("ID");
            String source = mRNAFeature.source();

            FeatureList gene = listGenes.selectByAttribute("Parent", geneid);
            FeatureI geneFeature = gene.get(0);
            ChromosomeSequence seq = chromosomeSequenceList.get(geneFeature.seqname());
            AccessionID geneAccessionID = new AccessionID(geneid);
            GeneSequence geneSequence = null;

            FeatureList cdsFeatures = gene.selectByType("CDS");
            FeatureI feature = cdsFeatures.get(0);
            Strand strand = Strand.POSITIVE;

            if (feature.location().isNegative()) {
                strand = strand.NEGATIVE;
            }
            cdsFeatures = cdsFeatures.sortByStart();







            String seqName = feature.seqname();
            FeatureI startCodon = null;
            FeatureI stopCodon = null;
            Integer startCodonBegin = null;
            Integer stopCodonEnd = null;
            String startCodonName = "";
            String stopCodonName = "";
            FeatureList startCodonList = gene.selectByAttribute("Note", "initial-exon");
            if (startCodonList != null && startCodonList.size() > 0) {
                startCodon = startCodonList.get(0);
                if (strand == Strand.NEGATIVE) {
                    startCodonBegin = startCodon.location().bioEnd();
                } else {
                    startCodonBegin = startCodon.location().bioStart();
                }
                startCodonName = startCodon.getAttribute("ID");
            }

            FeatureList stopCodonList = gene.selectByAttribute("Note", "final-exon");

            if (stopCodonList != null && stopCodonList.size() > 0) {
                stopCodon = stopCodonList.get(0);
                if (strand == Strand.NEGATIVE) {
                    stopCodonEnd = stopCodon.location().bioStart();
                } else {
                    stopCodonEnd = stopCodon.location().bioEnd();
                }
                stopCodonName = stopCodon.getAttribute("ID");

            }




            if (startCodonBegin == null) {
                if (strand == Strand.NEGATIVE) {
                    FeatureI firstFeature = cdsFeatures.get(0);

                    startCodonBegin = firstFeature.location().bioEnd();
                } else {
                    FeatureI firstFeature = cdsFeatures.get(0);

                    startCodonBegin = firstFeature.location().bioStart();
                }
            }

            if (stopCodonEnd == null) {
                if (strand == Strand.NEGATIVE) {
                    FeatureI lastFeature = cdsFeatures.get(cdsFeatures.size() - 1);
                    stopCodonEnd = lastFeature.location().bioStart();
                } else {
                    FeatureI lastFeature = cdsFeatures.get(cdsFeatures.size() - 1);
                    stopCodonEnd = lastFeature.location().bioEnd();
                }
            }
            //for gtf ordering can be strand based so first is last and last is first
            if (startCodonBegin > stopCodonEnd) {
                int temp = startCodonBegin;
                startCodonBegin = stopCodonEnd;
                stopCodonEnd = temp;
            }



            AccessionID transcriptAccessionID = new AccessionID(geneid);
            if (geneSequence == null) {
                geneSequence = seq.addGene(geneAccessionID, startCodonBegin, stopCodonEnd, strand);
                geneSequence.setSource(source);
            } else {

                if (startCodonBegin < geneSequence.getBioBegin()) {
                    geneSequence.setBioBegin(startCodonBegin);
                }
                if (stopCodonEnd > geneSequence.getBioBegin()) {
                    geneSequence.setBioEnd(stopCodonEnd);
                }

            }
            TranscriptSequence transcriptSequence = geneSequence.addTranscript(transcriptAccessionID, startCodonBegin, stopCodonEnd);
            if (startCodon != null) {
                if (startCodonName == null || startCodonName.length() == 0) {
                    startCodonName = geneid + "-start_codon-" + startCodon.location().bioStart() + "-" + startCodon.location().bioEnd();
                }
                transcriptSequence.addStartCodonSequence(new AccessionID(startCodonName), startCodon.location().bioStart(), startCodon.location().bioEnd());
            }
            if (stopCodon != null) {
                if (stopCodonName == null || stopCodonName.length() == 0) {
                    stopCodonName = geneid + "-stop_codon-" + stopCodon.location().bioStart() + "-" + stopCodon.location().bioEnd();
                }
                transcriptSequence.addStopCodonSequence(new AccessionID(stopCodonName), stopCodon.location().bioStart(), stopCodon.location().bioEnd());
            }

            for (FeatureI cdsFeature : cdsFeatures) {
                Feature cds = (Feature) cdsFeature;
                String cdsName = cds.getAttribute("ID");
                if (cdsName == null || cdsName.length() == 0) {
                    cdsName = geneid + "-cds-" + cds.location().bioStart() + "-" + cds.location().bioEnd();
                }
                AccessionID cdsAccessionID = new AccessionID(cdsName);
                ExonSequence exonSequence = geneSequence.addExon(cdsAccessionID, cdsFeature.location().bioStart(), cdsFeature.location().bioEnd());
                transcriptSequence.addCDS(cdsAccessionID, cdsFeature.location().bioStart(), cdsFeature.location().bioEnd(), cds.frame());
            }

        }

    }

    static public LinkedHashMap<String, ChromosomeSequence> loadFastaAddGeneFeaturesFromGeneMarkGTF(File fastaSequenceFile, File gffFile) throws Exception {
        LinkedHashMap<String, DNASequence> dnaSequenceList = FastaReaderHelper.readFastaDNASequence(fastaSequenceFile);
        LinkedHashMap<String, ChromosomeSequence> chromosomeSequenceList = GeneFeatureHelper.getChromosomeSequenceFromDNASequence(dnaSequenceList);
        FeatureList listGenes = GeneMarkGTFReader.read(gffFile.getAbsolutePath());
        addGeneMarkGTFGeneFeatures(chromosomeSequenceList, listGenes);
        return chromosomeSequenceList;
    }

    static public void addGeneMarkGTFGeneFeatures(LinkedHashMap<String, ChromosomeSequence> chromosomeSequenceList, FeatureList listGenes) throws Exception {
        Collection<String> geneIds = listGenes.attributeValues("gene_id");
        for (String geneid : geneIds) {
            //       if(geneid.equals("45_g")){
            //           int dummy =1;
            //       }
            FeatureList gene = listGenes.selectByAttribute("gene_id", geneid);
            FeatureI geneFeature = gene.get(0);
            ChromosomeSequence seq = chromosomeSequenceList.get(geneFeature.seqname());
            AccessionID geneAccessionID = new AccessionID(geneid);
            GeneSequence geneSequence = null;
            Collection<String> transcriptids = gene.attributeValues("transcript_id");
            for (String transcriptid : transcriptids) {
                // get all the individual features (exons, CDS regions, etc.) of this gene


                FeatureList transcriptFeature = listGenes.selectByAttribute("transcript_id", transcriptid);
                // now select only the coding regions of this gene
                FeatureList cdsFeatures = transcriptFeature.selectByType("CDS");
                // sort them
                cdsFeatures = cdsFeatures.sortByStart();

                FeatureI feature = cdsFeatures.get(0);
                Strand strand = Strand.POSITIVE;

                if (feature.location().isNegative()) {
                    strand = strand.NEGATIVE;
                }

                String seqName = feature.seqname();
                FeatureI startCodon = null;
                FeatureI stopCodon = null;
                Integer startCodonBegin = null;
                Integer stopCodonEnd = null;
                String startCodonName = "";
                String stopCodonName = "";
                FeatureList startCodonList = transcriptFeature.selectByType("start_codon");
                if (startCodonList != null && startCodonList.size() > 0) {
                    startCodon = startCodonList.get(0);
                    if (strand == Strand.POSITIVE) {
                        startCodonBegin = startCodon.location().bioStart();
                    } else {
                        startCodonBegin = startCodon.location().bioEnd();
                    }
                    startCodonName = startCodon.getAttribute("transcript_name");
                }

                FeatureList stopCodonList = transcriptFeature.selectByType("stop_codon");

                if (stopCodonList != null && stopCodonList.size() > 0) {
                    stopCodon = stopCodonList.get(0);
                    if (strand == Strand.POSITIVE) {
                        stopCodonEnd = stopCodon.location().bioEnd();
                    } else {
                        stopCodonEnd = stopCodon.location().bioStart();
                    }

                    stopCodonName = stopCodon.getAttribute("transcript_name");

                }




                if (startCodonBegin == null) {
                    if (strand == Strand.NEGATIVE) {
                        FeatureI firstFeature = cdsFeatures.get(0);

                        startCodonBegin = firstFeature.location().bioEnd();
                    } else {
                        FeatureI firstFeature = cdsFeatures.get(0);

                        startCodonBegin = firstFeature.location().bioStart();
                    }
                }

                if (stopCodonEnd == null) {
                    if (strand == Strand.NEGATIVE) {
                        FeatureI lastFeature = cdsFeatures.get(cdsFeatures.size() - 1);
                        stopCodonEnd = lastFeature.location().bioStart();
                    } else {
                        FeatureI lastFeature = cdsFeatures.get(cdsFeatures.size() - 1);
                        stopCodonEnd = lastFeature.location().bioEnd();
                    }
                }
                //for gtf ordering can be strand based so first is last and last is first
                if (startCodonBegin > stopCodonEnd) {
                    int temp = startCodonBegin;
                    startCodonBegin = stopCodonEnd;
                    stopCodonEnd = temp;
                }

                AccessionID transcriptAccessionID = new AccessionID(transcriptid);
                if (geneSequence == null) {
                    geneSequence = seq.addGene(geneAccessionID, startCodonBegin, stopCodonEnd, strand);
                    geneSequence.setSource(((Feature) feature).source());
                } else {
                    //if multiple transcripts for one gene make sure the gene is defined as the min and max start/end

                    if (startCodonBegin < geneSequence.getBioBegin()) {
                        geneSequence.setBioBegin(startCodonBegin);
                    }
                    if (stopCodonEnd > geneSequence.getBioBegin()) {
                        geneSequence.setBioEnd(stopCodonEnd);
                    }

                }
                TranscriptSequence transcriptSequence = geneSequence.addTranscript(transcriptAccessionID, startCodonBegin, stopCodonEnd);
                if (startCodon != null) {
                    if (startCodonName == null || startCodonName.length() == 0) {
                        startCodonName = transcriptid + "-start_codon-" + startCodon.location().bioStart() + "-" + startCodon.location().bioEnd();
                    }
                    transcriptSequence.addStartCodonSequence(new AccessionID(startCodonName), startCodon.location().bioStart(), startCodon.location().bioEnd());
                }
                if (stopCodon != null) {
                    if (stopCodonName == null || stopCodonName.length() == 0) {
                        stopCodonName = transcriptid + "-stop_codon-" + stopCodon.location().bioStart() + "-" + stopCodon.location().bioEnd();
                    }
                    transcriptSequence.addStopCodonSequence(new AccessionID(stopCodonName), stopCodon.location().bioStart(), stopCodon.location().bioEnd());
                }

                for (FeatureI cdsFeature : cdsFeatures) {
                    Feature cds = (Feature) cdsFeature;
                    // for genemark it appears frame of 2 =1 and frame of 1 = 2
                    // doesn't matter when you string cds regions together as one block
                    // but does make a difference when you try to make a protein sequence for each CDS region where
                    // you give up or borrow based on the frame value
                    // compared with gff like files and docs for geneid and glimmer where geneid and glimmer both do it the same
                    // way that appears to match the gff3 docs.
                    int frame = cds.frame();
                    if (frame == 1) {
                        frame = 2;
                    } else if (frame == 2) {
                        frame = 1;
                    } else {
                        frame = 0;
                    }
                    String cdsName = cds.getAttribute("transcript_name");
                    if (cdsName == null || cdsName.length() == 0) {
                        cdsName = transcriptid + "-cds-" + cds.location().bioStart() + "-" + cds.location().bioEnd();
                    }
                    AccessionID cdsAccessionID = new AccessionID(cdsName);
                    ExonSequence exonSequence = geneSequence.addExon(cdsAccessionID, cdsFeature.location().bioStart(), cdsFeature.location().bioEnd());
                    transcriptSequence.addCDS(cdsAccessionID, cdsFeature.location().bioStart(), cdsFeature.location().bioEnd(), frame);
                }
            }
        }

    }

    static public LinkedHashMap<String, ProteinSequence> getProteinSequences(Collection<ChromosomeSequence> chromosomeSequences) throws Exception {
        LinkedHashMap<String, ProteinSequence> proteinSequenceHashMap = new LinkedHashMap<String, ProteinSequence>();
        for (ChromosomeSequence dnaSequence : chromosomeSequences) {
            for (GeneSequence geneSequence : dnaSequence.getGeneSequences().values()) {
                for (TranscriptSequence transcriptSequence : geneSequence.getTranscripts().values()) {
                    //TODO remove?
//                    DNASequence dnaCodingSequence = transcriptSequence.getDNACodingSequence();
//                    System.out.println("CDS=" + dnaCodingSequence.getSequenceAsString());

                    try {
                        ProteinSequence proteinSequence = transcriptSequence.getProteinSequence();
//                        System.out.println(proteinSequence.getAccession().getID() + " " + proteinSequence);
                        if (proteinSequenceHashMap.containsKey(proteinSequence.getAccession().getID())) {
                            throw new Exception("Duplicate protein sequence id=" + proteinSequence.getAccession().getID() + " found at Gene id=" + geneSequence.getAccession().getID());
                        } else {
                            proteinSequenceHashMap.put(proteinSequence.getAccession().getID(), proteinSequence);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }

            }
        }
        return proteinSequenceHashMap;
    }

    static public LinkedHashMap<String, GeneSequence> getGeneSequences(Collection<ChromosomeSequence> chromosomeSequences) throws Exception {
        LinkedHashMap<String, GeneSequence> geneSequenceHashMap = new LinkedHashMap<String, GeneSequence>();
        for (ChromosomeSequence chromosomeSequence : chromosomeSequences) {
            for (GeneSequence geneSequence : chromosomeSequence.getGeneSequences().values()) {
                geneSequenceHashMap.put(geneSequence.getAccession().getID(), geneSequence);
            }
        }

        return geneSequenceHashMap;
    }

    public static void main(String args[]) throws Exception {
        if (false) {
            LinkedHashMap<String, ChromosomeSequence> chromosomeSequenceList = GeneFeatureHelper.loadFastaAddGeneFeaturesFromGeneMarkGTF(new File("/Users/Scooter/scripps/dyadic/analysis/454Scaffolds/454Scaffolds.fna"), new File("/Users/Scooter/scripps/dyadic/analysis/454Scaffolds/genemark_hmm.gtf"));
            LinkedHashMap<String, ProteinSequence> proteinSequenceList = GeneFeatureHelper.getProteinSequences(chromosomeSequenceList.values());
        }

        if (false) {
            LinkedHashMap<String, ChromosomeSequence> chromosomeSequenceList = GeneFeatureHelper.loadFastaAddGeneFeaturesFromGlimmerGFF3(new File("/Users/Scooter/scripps/dyadic/analysis/454Scaffolds/454Scaffolds.fna"), new File("/Users/Scooter/scripps/dyadic/GlimmerHMM/c1_glimmerhmm.gff"));
            LinkedHashMap<String, ProteinSequence> proteinSequenceList = GeneFeatureHelper.getProteinSequences(chromosomeSequenceList.values());
            //  for (ProteinSequence proteinSequence : proteinSequenceList.values()) {
            //      System.out.println(proteinSequence.getAccession().getID() + " " + proteinSequence);
            //  }
            FastaWriterHelper.writeProteinSequence(new File("/Users/Scooter/scripps/dyadic/GlimmerHMM/c1_predicted_glimmer.faa"), proteinSequenceList.values());

        }
        if (false) {
            GeneFeatureHelper.outputFastaSequenceLengthGFF3(new File("/Users/Scooter/scripps/dyadic/analysis/454Scaffolds/454Scaffolds.fna"), new File("/Users/Scooter/scripps/dyadic/analysis/454Scaffolds/c1scaffolds.gff3"));
        }



        try {
            if (true) {
                LinkedHashMap<String, ChromosomeSequence> dnaSequenceHashMap = GeneFeatureHelper.loadFastaAddGeneFeaturesFromGlimmerGFF3(new File("/Users/Scooter/scripps/dyadic/analysis/454Scaffolds/454Scaffolds-16.fna"), new File("/Users/Scooter/scripps/dyadic/GlimmerHMM/c1_glimmerhmm-16.gff"));

                LinkedHashMap<String, GeneSequence> geneSequenceHashMap = GeneFeatureHelper.getGeneSequences(dnaSequenceHashMap.values());
                Collection<GeneSequence> geneSequences = geneSequenceHashMap.values();
                FastaWriterHelper.writeGeneSequence(new File("/Users/Scooter/scripps/dyadic/outputGlimmer6/c1_glimmer_genes.fna"), geneSequences, true);


            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
