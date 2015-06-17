package aQute.lib.osgi;

import java.io.*;
import java.security.*;
import java.util.*;
import java.util.jar.*;
import java.util.regex.*;
import java.util.zip.*;

import static aQute.lib.io.IO.*;
import aQute.lib.base64.*;
import aQute.libg.reporter.*;

public class Jar implements Closeable {
	public static final Object[]		EMPTY_ARRAY	= new Jar[0];
	Map<String, Resource>				resources	= new TreeMap<String, Resource>();
	Map<String, Map<String, Resource>>	directories	= new TreeMap<String, Map<String, Resource>>();
	Manifest							manifest;
	boolean								manifestFirst;
	String								name;
	File								source;
	ZipFile								zipFile;
	long								lastModified;
	String								lastModifiedReason;
	Reporter							reporter;
	boolean								doNotTouchManifest;
	boolean								nomanifest;

	public Jar(String name) {
		this.name = name;
	}

	public Jar(String name, File dirOrFile) throws ZipException, IOException {
		this(name);
		source = dirOrFile;
		if (dirOrFile.isDirectory())
			FileResource.build(this, dirOrFile, Analyzer.doNotCopy);
		else if (dirOrFile.isFile()) {
			zipFile = ZipResource.build(this, dirOrFile);
		} else {
			throw new IllegalArgumentException("A Jar can only accept a valid file or directory: "
					+ dirOrFile);
		}
	}

	public Jar(String name, InputStream in, long lastModified) throws IOException {
		this(name);
		EmbeddedResource.build(this, in, lastModified);
	}

	public Jar(String name, String path) throws IOException {
		this(name);
		File f = new File(path);
		InputStream in = new FileInputStream(f);
		EmbeddedResource.build(this, in, f.lastModified());
		in.close();
	}

	public Jar(File jar) throws IOException {
		this(getName(jar), jar);
	}

	/**
	 * Make the JAR file name the project name if we get a src or bin directory.
	 * 
	 * @param f
	 * @return
	 */
	private static String getName(File f) {
		f = f.getAbsoluteFile();
		String name = f.getName();
		if (name.equals("bin") || name.equals("src"))
			return f.getParentFile().getName();
		else {
			if (name.endsWith(".jar"))
				name = name.substring(0, name.length() - 4);
			return name;
		}
	}

	public Jar(String string, InputStream resourceAsStream) throws IOException {
		this(string, resourceAsStream, 0);
	}

	public void setName(String name) {
		this.name = name;
	}

	public String toString() {
		return "Jar:" + name;
	}

	public boolean putResource(String path, Resource resource) {
		return putResource(path, resource, true);
	}

	public boolean putResource(String path, Resource resource, boolean overwrite) {
		updateModified(resource.lastModified(), path);
		while (path.startsWith("/"))
			path = path.substring(1);

		if (path.equals("META-INF/MANIFEST.MF")) {
			manifest = null;
			if (resources.isEmpty())
				manifestFirst = true;
		}
		String dir = getDirectory(path);
		Map<String, Resource> s = directories.get(dir);
		if (s == null) {
			s = new TreeMap<String, Resource>();
			directories.put(dir, s);
			int n = dir.lastIndexOf('/');
			while (n > 0) {
				String dd = dir.substring(0, n);
				if (directories.containsKey(dd))
					break;
				directories.put(dd, null);
				n = dd.lastIndexOf('/');
			}
		}
		boolean duplicate = s.containsKey(path);
		if (!duplicate || overwrite) {
			resources.put(path, resource);
			s.put(path, resource);
		}
		return duplicate;
	}

	public Resource getResource(String path) {
		return resources.get(path);
	}

	private String getDirectory(String path) {
		int n = path.lastIndexOf('/');
		if (n < 0)
			return "";

		return path.substring(0, n);
	}

	public Map<String, Map<String, Resource>> getDirectories() {
		return directories;
	}

	public Map<String, Resource> getResources() {
		return resources;
	}

	public boolean addDirectory(Map<String, Resource> directory, boolean overwrite) {
		boolean duplicates = false;
		if (directory == null)
			return false;

		for (Map.Entry<String, Resource> entry : directory.entrySet()) {
			String key = entry.getKey();
			if (!key.endsWith(".java")) {
				duplicates |= putResource(key, (Resource) entry.getValue(), overwrite);
			}
		}
		return duplicates;
	}

	public Manifest getManifest() throws IOException {
		if (manifest == null) {
			Resource manifestResource = getResource("META-INF/MANIFEST.MF");
			if (manifestResource != null) {
				InputStream in = manifestResource.openInputStream();
				manifest = new Manifest(in);
				in.close();
			}
		}
		return manifest;
	}

	public boolean exists(String path) {
		return resources.containsKey(path);
	}

	public void setManifest(Manifest manifest) {
		manifestFirst = true;
		this.manifest = manifest;
	}

	public void write(File file) throws Exception {
		try {
			OutputStream out = new FileOutputStream(file);
			write(out);
			out.close();
			return;

		} catch (Exception t) {
			file.delete();
			throw t;
		}
	}

	public void write(String file) throws Exception {
		write(new File(file));
	}

	public void write(OutputStream out) throws IOException {
		ZipOutputStream jout = nomanifest || doNotTouchManifest ? new ZipOutputStream(out)
				: new JarOutputStream(out);
		Set<String> done = new HashSet<String>();

		Set<String> directories = new HashSet<String>();
		if (doNotTouchManifest) {
			Resource r = getResource("META-INF/MANIFEST.MF");
			if (r != null) {
				writeResource(jout, directories, "META-INF/MANIFEST.MF", r);
				done.add("META-INF/MANIFEST.MF");
			}
		} else
			doManifest(done, jout);

		for (Map.Entry<String, Resource> entry : getResources().entrySet()) {
			// Skip metainf contents
			if (!done.contains(entry.getKey()))
				writeResource(jout, directories, (String) entry.getKey(), (Resource) entry
						.getValue());
		}
		jout.finish();
	}

	private void doManifest(Set<String> done, ZipOutputStream jout) throws IOException {
		if (nomanifest)
			return;

		JarEntry ze = new JarEntry("META-INF/MANIFEST.MF");
		jout.putNextEntry(ze);
		writeManifest(jout);
		jout.closeEntry();
		done.add(ze.getName());
	}

	/**
	 * Cleanup the manifest for writing. Cleaning up consists of adding a space
	 * after any \n to prevent the manifest to see this newline as a delimiter.
	 * 
	 * @param out
	 *            Output
	 * @throws IOException
	 */

	public void writeManifest(OutputStream out) throws IOException {
		writeManifest(getManifest(), out);
	}

	public static void writeManifest(Manifest manifest, OutputStream out) throws IOException {
		if (manifest == null)
			return;

		manifest = clean(manifest);
		manifest.write(out);
	}

	private static Manifest clean(Manifest org) {

		Manifest result = new Manifest();
		for (Map.Entry<?, ?> entry : org.getMainAttributes().entrySet()) {
			String nice = clean((String) entry.getValue());
			result.getMainAttributes().put(entry.getKey(), nice);
		}
		for (String name : org.getEntries().keySet()) {
			Attributes attrs = result.getAttributes(name);
			if (attrs == null) {
				attrs = new Attributes();
				result.getEntries().put(name, attrs);
			}

			for (Map.Entry<?, ?> entry : org.getAttributes(name).entrySet()) {
				String nice = clean((String) entry.getValue());
				attrs.put((Attributes.Name) entry.getKey(), nice);
			}
		}
		return result;
	}

	private static String clean(String s) {
		if (s.indexOf('\n') < 0)
			return s;

		StringBuffer sb = new StringBuffer(s);
		for (int i = 0; i < sb.length(); i++) {
			if (sb.charAt(i) == '\n')
				sb.insert(++i, ' ');
		}
		return sb.toString();
	}

	private void writeResource(ZipOutputStream jout, Set<String> directories, String path,
			Resource resource) throws IOException {
		if (resource == null)
			return;

		createDirectories(directories, jout, path);
		ZipEntry ze = new ZipEntry(path);
		ze.setMethod(ZipEntry.DEFLATED);
		long lastModified = resource.lastModified();
		if (lastModified == 0L) {
			lastModified = System.currentTimeMillis();
		}
		ze.setTime(lastModified);
		if (resource.getExtra() != null)
			ze.setExtra(resource.getExtra().getBytes());
		jout.putNextEntry(ze);
		try {
			resource.write(jout);
		} catch (Exception e) {
			throw new IllegalArgumentException("Cannot write resource: " + path + " " + resource
					+ " exception: " + e);
		}
		jout.closeEntry();
	}

	void createDirectories(Set<String> directories, ZipOutputStream zip, String name)
			throws IOException {
		int index = name.lastIndexOf('/');
		if (index > 0) {
			String path = name.substring(0, index);
			if (directories.contains(path))
				return;
			createDirectories(directories, zip, path);
			ZipEntry ze = new ZipEntry(path + '/');
			zip.putNextEntry(ze);
			zip.closeEntry();
			directories.add(path);
		}
	}

	public String getName() {
		return name;
	}

	/**
	 * Add all the resources in the given jar that match the given filter.
	 * 
	 * @param sub
	 *            the jar
	 * @param filter
	 *            a pattern that should match the resoures in sub to be added
	 */
	public boolean addAll(Jar sub, Instruction filter) {
		return addAll(sub, filter, "");
	}

	/**
	 * Add all the resources in the given jar that match the given filter.
	 * 
	 * @param sub
	 *            the jar
	 * @param filter
	 *            a pattern that should match the resoures in sub to be added
	 */
	public boolean addAll(Jar sub, Instruction filter, String destination) {
		boolean dupl = false;
		for (String name : sub.getResources().keySet()) {
			if ("META-INF/MANIFEST.MF".equals(name))
				continue;

			if (filter == null || filter.matches(name) != filter.isNegated())
				dupl |= putResource(Processor.appendPath(destination, name), sub.getResource(name),
						true);
		}
		return dupl;
	}

	public void close() {
		if (zipFile != null)
			try {
				zipFile.close();
			} catch (IOException e) {
				// Ignore
			}
		resources = null;
		directories = null;
		manifest = null;
		source = null;
	}

	public long lastModified() {
		return lastModified;
	}

	public void updateModified(long time, String reason) {
		if (time > lastModified) {
			lastModified = time;
			lastModifiedReason = reason;
		}
	}

	public void setReporter(Reporter reporter) {
		this.reporter = reporter;
	}

	public boolean hasDirectory(String path) {
		return directories.get(path) != null;
	}

	public List<String> getPackages() {
		List<String> list = new ArrayList<String>(directories.size());

		for (Iterator<String> i = directories.keySet().iterator(); i.hasNext();) {
			String path = i.next();
			String pack = path.replace('/', '.');
			list.add(pack);
		}
		return list;
	}

	public File getSource() {
		return source;
	}

	public boolean addAll(Jar src) {
		return addAll(src, null);
	}

	public boolean rename(String oldPath, String newPath) {
		Resource resource = remove(oldPath);
		if (resource == null)
			return false;

		return putResource(newPath, resource);
	}

	public Resource remove(String path) {
		Resource resource = resources.remove(path);
		String dir = getDirectory(path);
		Map<String, Resource> mdir = directories.get(dir);
		// must be != null
		mdir.remove(path);
		return resource;
	}

	/**
	 * Make sure nobody touches the manifest! If the bundle is signed, we do not
	 * want anybody to touch the manifest after the digests have been
	 * calculated.
	 */
	public void setDoNotTouchManifest() {
		doNotTouchManifest = true;
	}

	/**
	 * Calculate the checksums and set them in the manifest.
	 */

	public void calcChecksums(String algorithms[]) throws Exception {
		if ( algorithms == null )
			algorithms = new String[] {"SHA", "MD5"};
		
		MessageDigest digests[] = new MessageDigest[algorithms.length];
		int n = 0;
		for  ( String algorithm : algorithms )
			digests[n++] = MessageDigest.getInstance(algorithm);
		
		byte buffer[] = new byte[30000];

		for (Map.Entry<String, Resource> entry : resources.entrySet()) {
			Resource r = entry.getValue();
			Attributes attributes = getManifest().getAttributes(entry.getKey());
			InputStream in = r.openInputStream();
			try {
				for (MessageDigest d : digests)
					d.reset();
				int size = in.read(buffer);
				while (size > 0) {
					for (MessageDigest d : digests)
						d.update(buffer, 0, size);
					size = in.read(buffer);
				}
			} finally {
				in.close();
			}
			for (MessageDigest d : digests)
				attributes.putValue(d.getAlgorithm() + "-Digest", Base64.encodeBase64(d.digest()));
		}
	}

	
	
	Pattern BSN = Pattern.compile("\\s*([-\\w\\d\\._]+)\\s*;.*");
	public String getBsn() throws IOException {
		Manifest m = getManifest();
		if ( m == null)
			return null;
		
		String s  = m.getMainAttributes().getValue(Constants.BUNDLE_SYMBOLICNAME);
		Matcher matcher = BSN.matcher(s);
		if ( matcher.matches()) {
			return matcher.group(1); 
		}
		return null;
	}
	
	public String getVersion() throws IOException {
		Manifest m = getManifest();
		if ( m == null)
			return null;

		String s = m.getMainAttributes().getValue(Constants.BUNDLE_VERSION);
		if ( s == null)
			return null;
		
		return s.trim();
	}
	


	/**
	 * Expand the JAR file to a directory.
	 * 
	 * @param dir the dst directory, is not required to exist
	 * @throws Exception if anything does not work as expected.
	 */
	public void expand(File dir) throws IOException {
		dir = dir.getAbsoluteFile();
		dir.mkdirs();
		if ( !dir.isDirectory()) {
			throw new IllegalArgumentException("Not a dir: " + dir.getAbsolutePath());
		}
			
		for ( Map.Entry<String,Resource> entry : getResources().entrySet()) {
			File f = getFile( dir, entry.getKey());
			f.getParentFile().mkdirs();
			copy(entry.getValue().openInputStream(), f);
		}
	}
	
		
}
