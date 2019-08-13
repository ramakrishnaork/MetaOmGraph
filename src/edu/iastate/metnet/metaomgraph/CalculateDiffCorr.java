package edu.iastate.metnet.metaomgraph;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.swing.JOptionPane;

import org.apache.commons.math3.analysis.function.Atanh;
import org.apache.commons.math3.distribution.NormalDistribution;

import edu.iastate.metnet.metaomgraph.ui.BlockingProgressDialog;

public class CalculateDiffCorr {

	private String geneList;
	private String featureName;
	private int featureIndex;
	// group indices
	Collection<Integer> grp1Ind;
	Collection<Integer> grp2Ind;
	private String g1name;
	private String g2name;
	private MetaOmProject myProject;
	private int method; // 0 paramentric fisher test 1 permutation with FT 2 perm without transform

	private List<Double> corrGrp1;
	private List<Double> corrGrp2;
	private List<Double> zVals1;
	private List<Double> zVals2;
	private List<Double> diffZvals;
	private List<Double> zScores;
	private List<Double> pValues;

	private boolean[] excluded;

	public CalculateDiffCorr(String genelist, String string, int featureInd, List<String> grp1, List<String> grp2,
			String name1, String name2, MetaOmProject myProject, int m) {

		this.geneList = genelist;
		this.myProject = myProject;
		featureName = string;
		featureIndex = featureInd;
		// create collection of indices
		grp1Ind = getIndices(grp1);
		grp2Ind = getIndices(grp2);
		g1name = name1;
		g2name = name2;

		method = m;
		excluded = MetaOmAnalyzer.getExclude();
	}

	/**
	 * get indices of samples by sample ID
	 * 
	 * @param listDC
	 * @return
	 */
	private Collection<Integer> getIndices(List<String> listDC) {
		Collection<Integer> res = new ArrayList<>();
		String[] dataColumnheaders = myProject.getDataColumnHeaders();
		for (int i = 0; i < dataColumnheaders.length; i++) {
			if (listDC.contains(dataColumnheaders[i])) {
				res.add(i);
			}
		}
		return res;
	}

	public void methodParametric() throws IOException {
		// compute corrGrp1 and corrGrp2
		corrGrp1 = new ArrayList<>();
		corrGrp2 = new ArrayList<>();
		Collection<Integer> g1Ind = grp1Ind;
		Collection<Integer> g2Ind = grp2Ind;

		// get the target data
		final int[] entries = myProject.getGeneListRowNumbers(geneList);
		// apply transformations if specified
		double[] targetData = myProject.getAllData(featureIndex, false);
		// split targetData into two groups target1 and target2
		double target1[] = new double[grp1Ind.size()];
		double target2[] = new double[grp2Ind.size()];

		int i1, i2;
		i1 = i2 = 0;
		for (int k = 0; k < targetData.length; k++) {
			if (excluded != null && excluded[k]) {
				continue;
			}
			if (g1Ind.contains(k)) {
				target1[i1++] = targetData[k];
			} else if (g2Ind.contains(k)) {
				target2[i2++] = targetData[k];
			}
		}

		// calculate two lists of correlation wrt to target1 and target2
		final BlockingProgressDialog progress = new BlockingProgressDialog(MetaOmGraph.getMainWindow(),
				"Calculating...", "", 0L, entries.length, true);
		SwingWorker analyzeWorker = new SwingWorker() {

			boolean errored = false;

			public Object construct() {
				try {
					CorrelationCalc calcy1 = new CorrelationCalc(target1, excluded);
					CorrelationCalc calcy2 = new CorrelationCalc(target2, excluded);

					int i = 0;
					do {
						progress.setProgress(i);
						double[] thisdata;
						thisdata = myProject.getAllData(entries[i], false);

						// split targetData into two groups target1 and target2
						double data1[] = new double[grp1Ind.size()];
						double data2[] = new double[grp2Ind.size()];
						int i1 = 0, i2 = 0;
						for (int k = 0; k < thisdata.length; k++) {
							if (excluded != null && excluded[k]) {
								continue;
							}
							if (g1Ind.contains(k)) {
								data1[i1++] = thisdata[k];
							} else if (g2Ind.contains(k)) {
								data2[i2++] = thisdata[k];
							}
						}

						corrGrp1.add(calcy1.pearsonCorrelation(data1, myProject.mayContainBlankValues(),
								myProject.getBlankValue()));
						corrGrp2.add(calcy2.pearsonCorrelation(data2, myProject.mayContainBlankValues(),
								myProject.getBlankValue()));

						i++;
						if (i >= entries.length)
							break;
					} while (!progress.isCanceled());

				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return null;
			}

			public void finished() {
				if (progress.isCanceled()) {
					// JOptionPane.showMessageDialog(null, "click cancelled");
					errored = true;
					progress.dispose();
				}
				if ((!progress.isCanceled()) && (!errored)) {

				}
				progress.dispose();
			}
		};

		analyzeWorker.start();
		progress.setVisible(true);
	}

	public void doCalc() throws IOException {

		// get current method
		if (this.method == 0) {
			methodParametric();
		} else if (this.method == 1) {
			JOptionPane.showMessageDialog(null, "No method");
		} else {
			JOptionPane.showMessageDialog(null, "Error");
		}

		
		//after computation of corr vals
		zVals1 = getConveredttoZ(this.corrGrp1);
		zVals2 = getConveredttoZ(this.corrGrp2);
		diffZvals = getDiff(zVals1, zVals2);
		zScores = computeZscores(diffZvals,getGrp1Size(),getGrp2Size());
		pValues = computePVals(zScores);
		
	}

	public List<String> getFeatureNames() {
		String[] featNamesArr = myProject.getDefaultRowNames(myProject.getGeneListRowNumbers(geneList));
		return Arrays.asList(featNamesArr);
	}

	public int getGrp1Size() {
		return grp1Ind.size();
	}

	public int getGrp2Size() {
		return grp2Ind.size();
	}

	public List<Double> getCorrGrp1() {
		return corrGrp1;
	}

	public List<Double> getCorrGrp2() {
		return corrGrp2;
	}
	
	/**
	 * convert r values to z applying Fisher's transform
	 * 
	 * @param rVals
	 * @return
	 */
	public static List<Double> getConveredttoZ(List<Double> rVals) {
		List<Double> res = new ArrayList<>();
		Atanh atan = new Atanh();
		for (double d : rVals) {
			res.add(atan.value(d));
			// JOptionPane.showMessageDialog(null, "val:"+d+" atan:"+atan.value(d));
		}
		return res;
	}
	
	/**
	 * returns difference between two correlation value arrays
	 * @param rVals1
	 * @param rVals2
	 * @return
	 */
	public static List<Double> getDiff(List<Double> rVals1, List<Double> rVals2) {
		List<Double> res = new ArrayList<>();
		for (int i = 0; i < rVals1.size(); i++) {
			res.add(rVals1.get(i) - rVals2.get(i));
		}
		return res;
	}
	
	/**
	 * Get z score array from two correlation arrays
	 * @param rVals1
	 * @param rVals2
	 * @return
	 */
	
	
	public static List<Double> computeZscores(List<Double> diff, int n1,int n2) {
		
		List<Double> res = new ArrayList<>();
		for (int i = 0; i < diff.size(); i++) {
			double thisZ = diff.get(i);
			double denom = Math.sqrt((1 / ((double) n1 - 3)) + (1 / ((double) n2 - 3)));
			// JOptionPane.showMessageDialog(null, "denom:" + denom);
			thisZ = thisZ / denom;
			res.add(thisZ);
		}
		return res;
	}

	/**
	 * get the pvalues using normal distribution
	 * @param zScores list of z scores
	 * @return
	 */
	public static List<Double> computePVals(List<Double> zScores) {
		List<Double> res = new ArrayList<>();
		NormalDistribution nob = new NormalDistribution();

		for (int i = 0; i < zScores.size(); i++) {
			double thisZ = zScores.get(i);
			if (thisZ > 0) {
				thisZ = thisZ * -1;
			}
			res.add(nob.cumulativeProbability(thisZ) * 2);
		}
		return res;
	}
	
	/**
	 * get z vals
	 * @param index
	 * @return
	 */
	public List<Double> getzVals(int index) {
		if(index==1) {
			return zVals1;
		}
		else if(index==2) {
			return zVals2;
		}
		return null;
	}
	
	public List<Double> getDiffZVals() {
		return diffZvals;
	}
	
	public List<Double> getzScores() {
		return zScores;
	}
	
	public List<Double> getpValues() {
		return pValues;
	}
	
	
	

}
