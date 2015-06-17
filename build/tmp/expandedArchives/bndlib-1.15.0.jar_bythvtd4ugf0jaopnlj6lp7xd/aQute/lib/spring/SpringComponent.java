package aQute.lib.spring;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import javax.xml.transform.*;
import javax.xml.transform.stream.*;

import aQute.bnd.service.*;
import aQute.lib.osgi.*;

/**
 * This component is called when we find a resource in the META-INF/*.xml
 * pattern. We parse the resource and and the imports to the builder.
 * 
 * Parsing is done with XSLT (first time I see the use of having XML for the
 * Spring configuration files!).
 * 
 * @author aqute
 * 
 */
public class SpringComponent implements AnalyzerPlugin {
	static Transformer transformer;
	static Pattern SPRING_SOURCE = Pattern.compile("META-INF/spring/.*\\.xml");
	static Pattern QN = Pattern.compile("[_A-Za-z$][_A-Za-z0-9$]*(\\.[_A-Za-z$][_A-Za-z0-9$]*)*");

	public static Set<CharSequence> analyze(InputStream in) throws Exception {
		if (transformer == null) {
			TransformerFactory tf = TransformerFactory.newInstance();
			Source source = new StreamSource(SpringComponent.class
					.getResourceAsStream("extract.xsl"));
			transformer = tf.newTransformer(source);
		}

		Set<CharSequence> refers = new HashSet<CharSequence>();

		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		Result r = new StreamResult(bout);
		Source s = new StreamSource(in);
		transformer.transform(s, r);

		ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
		bout.close();

		BufferedReader br = new BufferedReader(new InputStreamReader(bin));

		String line = br.readLine();
		while (line != null) {
			line = line.trim();
			if (line.length() > 0) {
				String parts[] = line.split("\\s*,\\s*");
				for (int i = 0; i < parts.length; i++) {
					int n = parts[i].lastIndexOf('.');
					if (n > 0) {
						refers.add(parts[i].subSequence(0, n));
					}
				}
			}
			line = br.readLine();
		}
		br.close();
		return refers;
	}

	@SuppressWarnings("unchecked")
    public boolean analyzeJar(Analyzer analyzer) throws Exception {
	    Jar jar = analyzer.getJar();
		Map dir = (Map) jar.getDirectories().get("META-INF/spring");
		if ( dir == null || dir.isEmpty())
			return false;
		
		for (Iterator i = dir.entrySet().iterator(); i.hasNext();) {
			Map.Entry entry = (Map.Entry) i.next();
			String path = (String) entry.getKey();
			Resource resource = (Resource) entry.getValue();
			if (SPRING_SOURCE.matcher(path).matches()) {
				try {
				InputStream in = resource.openInputStream();
				Set set = analyze(in);
				in.close();
				for (Iterator r = set.iterator(); r.hasNext();) {
					String pack = (String) r.next();
					if ( !QN.matcher(pack).matches())
					    analyzer.warning("Package does not seem a package in spring resource ("+path+"): " + pack );
					if (!analyzer.getReferred().containsKey(pack))
						analyzer.getReferred().put(pack, new LinkedHashMap());
				}
				} catch( Exception e ) {
					analyzer.error("Unexpected exception in processing spring resources("+path+"): " + e );
				}
			}
		}
		return false;
	}

}
