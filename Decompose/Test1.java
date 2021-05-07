public class Test1 {

	public static void main(String[] args) throws Exception {
		// TODO Auto-generated method stub

		// String netfile = "C:\\Users\\lcheng\\Desktop\\decomp\\test.pnml";
		// String netfile = "D:\\temp\\exe3.pnml";
		// String netfile = "D:\\temp\\3_2_10_large.pnml";
		// String netfile="D:\\temp\\3_2.pnml";
		// String netfile="D:\\temp\\net2.pnml";

		boolean TEST = true;
		String netfile;
		String opath;
		int dis;
		if (TEST) {
			netfile = "C:\\Users\\lcheng\\Desktop\\decomp\\prEm6.pnml";
			opath = "C:\\Users\\lcheng\\Desktop\\decomp\\";
			dis = 5;
		} else {
			netfile = args[0];
			opath = args[1];
			dis = Integer.valueOf(args[2]);
		}

		NetDecom dn = new NetDecom(netfile, opath, dis);

		dn.run();
	}
}
