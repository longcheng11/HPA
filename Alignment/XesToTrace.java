import org.deckfour.xes.in.XParserRegistry;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;

/**
 * Created by lcheng on 9/25/2017.
 */

public class XesToTrace {

	public static void main(String[] args) throws Exception {
		boolean TEST = false;

		String logfile;
		String opath;
		if (TEST) {
			logfile = "C:\\Users\\lcheng\\Desktop\\decomp\\test\\trace_100_010.xes.gz";
			opath = "C:\\Users\\lcheng\\Desktop\\decomp\\trace_100_010.txt";
		} else {
			logfile = args[0];
			opath = args[1];
		}

		File ofile = new File(opath);

		XLog xtraces = XParserRegistry.instance().currentDefault().parse(new File(logfile)).get(0);
		int X = xtraces.size();

		// output the traces
		int k = 0;
		String envs;
		BufferedWriter br = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(ofile)));
		for (XTrace ti : xtraces) {
			br.write(String.valueOf(k));
			for (XEvent ei : ti) {
				envs = ei.getAttributes().get("concept:name").toString();
				br.write("," + envs.replace(' ', '_').replace("(", "").replace(")", ""));
			}
			k++;
			br.write("\n");

			if (k % 100000 == 0) {
				br.flush();
			}
		}

		br.flush();
		br.close();
		System.out.println(X + " traces has been extracted");
	}

}
