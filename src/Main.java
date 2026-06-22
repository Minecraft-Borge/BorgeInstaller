import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public class Main {
	public static final ClassLoader CLASS_LOADER = Main.class.getClassLoader();
	public static final String JAR_DOWNLOAD_URL = "https://github.com/Minecraft-Borge/MinecraftBorge-%s-%s/releases/download/%s/minecraftborge-%s-%s.jar";

	public static final String LATEST_URL = "https://minecraftborge.net/files/versions.xml";
	public static final String LATEST_URL_FALLBACK = "https://minecraft-borge.github.io/files/versions.xml";
	public static boolean latestUseFallback = false;

	public static final String ASM_URL = "https://minecraftborge.net/files/asm/asm-9.9.jar";
	public static final String ASM_URL_FALLBACK = "https://minecraft-borge.github.io/files/asm/asm-9.9.jar";
	public static boolean asmUseFallback = false;

	public static final Map<String, String> SERVER_DOWNLOADS = new HashMap<>();
	public static final Map<String, String> CLIENT_DOWNLOADS = new HashMap<>();
	public static final Map<String, String> CLIENT_JSON_DOWNLOADS = new HashMap<>();

	static {
		SERVER_DOWNLOADS.put("b1.7.3", "https://vault.omniarchive.uk/archive/java/server-beta/b1.7/b1.7.3.jar");
	}
	static {
		CLIENT_DOWNLOADS.put("b1.7.3", "https://piston-data.mojang.com/v1/objects/43db9b498cb67058d2e12d394e6507722e71bb45/client.jar");
		CLIENT_JSON_DOWNLOADS.put("b1.7.3", "https://piston-meta.mojang.com/v1/packages/66cafaf8ea5a7b76547eb24c848832af44255ba1/b1.7.3.json");
	}

	public static String formatURL(String mcVersion, String borgeVersion, String side) {
		return String.format(JAR_DOWNLOAD_URL, mcVersion, side, borgeVersion, mcVersion, borgeVersion);
	}

	private static URL getResource(String path) {
		return Objects.requireNonNull(CLASS_LOADER.getResource(path), "Null resource: " + path);
	}
	public static void main(String[] args) throws Exception {
		JFrame frame = new JFrame("MinecraftBorge installer");
		frame.setLayout(new BorderLayout(5, 5));
		JTextArea area = new JTextArea();
		area.setEditable(false);
		area.setFont(new Font("Sans-Serif", Font.PLAIN, 15));
		area.setAutoscrolls(true);
		area.scrollRectToVisible(new Rectangle());
		JTextField input = new JTextField();
		input.setFont(new Font("Sans-Serif", Font.PLAIN, 15));
		input.setRequestFocusEnabled(true);
		frame.setIconImage(ImageIO.read(getResource("icon.png")));
		JLabel label = new JLabel(new Icon() {
			private final Image icon;
			{
				this.icon = ImageIO.read(getResource("splash.png"));
			}

			@Override
			public void paintIcon(Component c, Graphics g, int x, int y) {
				g.drawImage(this.icon, 0, 0, this.getIconWidth(), this.getIconHeight(), null);
			}

			@Override
			public int getIconWidth() {
				return this.icon.getWidth(null);
			}

			@Override
			public int getIconHeight() {
				return this.icon.getHeight(null);
			}
		});
		frame.add(area, "Center");
		frame.add(input, "South");
		frame.add(label, "North");
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				frame.dispose();
			}

			@Override
			public void windowClosed(WindowEvent e) {
				System.exit(0);
			}
		});
		input.addActionListener(e -> {
			input.setEditable(false);
			input.setText("");
			processCommand(input, e.getActionCommand());
		});
		Toolkit toolkit = Toolkit.getDefaultToolkit();
		Dimension screenSize = toolkit.getScreenSize();
		frame.setMinimumSize(new Dimension(screenSize.width / 2, screenSize.height / 2));
		frame.setLocation(screenSize.width / 4, screenSize.height / 4);
		frame.setVisible(true);

		System.setOut(new TextAreaPrintStream(area));
		System.setErr(new TextAreaPrintStream(area));

		System.out.println("Welcome to the MinecraftBorge installation tool! Type 'help' for a list of commands.");
	}

	public static InputStream getLatestXMLStream() throws IOException {
		if (latestUseFallback) {
			return new URL(LATEST_URL_FALLBACK).openStream();
		} else {
			try {
				return new URL(LATEST_URL).openStream();
			} catch (Exception e) {
				latestUseFallback = true;
				return getLatestXMLStream();
			}
		}
	}
	public static InputStream getASMStream() throws IOException {
		if (asmUseFallback) {
			return new URL(ASM_URL_FALLBACK).openStream();
		} else {
			try {
				return new URL(ASM_URL_FALLBACK).openStream();
			} catch (Exception e) {
				asmUseFallback = true;
				return getASMStream();
			}
		}
	}

	private static void processCommand(final JTextField input, final String command) {
		new Thread(() -> {
			try {
				System.out.println("> " + command);
				final String[] cmd = splitIntoArgs(command);
				final String type = cmd[0];
				final String[] args = cmd.length > 1 ? new String[cmd.length - 1] : new String[0];
				if (args.length > 0) System.arraycopy(cmd, 1, args, 0, args.length);
				command(type, args);
			} catch (Throwable e) {
				e.printStackTrace(System.err);
			}
			input.setEditable(true);
		}, "").start();
	}

	private static String[] splitIntoArgs(final String command) {
		String[] split = command.split(" ");
		List<String> list = new ArrayList<>();
		for (String s : split) {
			list.add(s.replace("%20", " "));
		}
		return list.toArray(new String[0]);
	}

	private static void command(final String type, final String[] args) {
		switch (type.toLowerCase(Locale.ROOT)) {
			case "exit":
				System.exit(0);
				break;
			case "help":
				help();
				break;
			case "target":
				target(args);
				break;
			case "install":
				install(args);
				break;
			default:
				System.err.println("Unknown or misspelled command: " + type);
		}
	}

	private static boolean isDotMinecraftDir = false;
	private static File target = null;

	private static void help() {
		System.out.println("'exit': Quits the application");
		System.out.println("'help': Shows this list");
		System.out.println("'target [dir]': Gets or sets the target installation directory");
		System.out.println("'install <side> <mcversion> <borgeversion>': Installs the specified Borge version");
	}
	private static void target(final String[] args) {
		if (args.length == 0) {
			System.out.println("The current target directory is " + target);
			return;
		}
		if (args.length == 1 && args[0].equalsIgnoreCase("%AppData%")) {
			isDotMinecraftDir = true;
			target = getAppDir("minecraft");
			target = new File(target, "versions/");
			if (!target.isDirectory() && !target.mkdirs()) {
				System.err.println("The working directory could not be created");
				target = null;
				return;
			}
		} else {
			isDotMinecraftDir = false;
			StringBuilder builder = new StringBuilder();
			builder.append(args[0]);
			for (int i = 1; i < args.length; i++) {
				builder.append(" ").append(args[i]);
			}
			File file = new File(builder.toString());
			if (!file.isDirectory() && !file.mkdirs()) {
				System.err.println("The working directory could not be created");
				target = null;
				return;
			}
			target = file;
		}
		System.out.println("Set target to: '" + target.getAbsolutePath() + "'");
	}

	private static void install(final String[] args) {
		switch (args.length) {
			case 0:
				System.err.println("Missing 'side', 'mcversion' and 'borgeversion' arguments");
				return;
			case 1:
				System.err.println("Missing 'mcversion' and 'borgeversion' arguments");
				return;
			case 2:
				System.err.println("Missing 'borgeversion' argument");
				return;
		}

		String side = args[0].toLowerCase(Locale.ROOT);
		String mcversion = args[1].toLowerCase(Locale.ROOT);
		String borgeversion = args[2].toLowerCase(Locale.ROOT);

		if (!"server".equals(side) && !"client".equals(side)) {
			System.err.println("Side must either be 'server' or 'client'!");
			return;
		}
		final boolean server = "server".equals(side);

		if (isDotMinecraftDir && server) {
			System.err.println("You probably should not install the server in the .minecraft folder!!!");
		}

		if ("latest".equals(borgeversion)) {
			System.out.println("Attempting to retrieve versions.xml from website");
			String latest = null;
			try (InputStream in = getLatestXMLStream()) {
				DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
				DocumentBuilder builder = factory.newDocumentBuilder();
				Document document = builder.parse(in);
				Element root = document.getDocumentElement();
				String namedSide = server ? "Server" : "Client";
				Element list = (Element) root.getElementsByTagName(namedSide).item(0);
				NodeList versions = list.getElementsByTagName("version");
				for (int i = 0; i < versions.getLength(); i++) {
					Node node = versions.item(i);
					if (node.getNodeType() == Node.ELEMENT_NODE) {
						Element version = (Element) node;
						if (version.getAttribute("minecraft").equals(mcversion)) {
							latest = version.getTextContent();
							System.out.println("Found " + latest);
						}
					}
				}
			} catch (Exception e) {
				throw new RuntimeException("Failed to connect to servers", e);
			}
			if (latest == null) {
				System.err.println("Could not find latest Borge version for " + mcversion);
				return;
			}
			borgeversion = latest;
		}

		File targetOld = target;
		long timer;
		if (isDotMinecraftDir) {
			target = new File(target, "minecraftborge-" + mcversion);
			if (!target.isDirectory() && !target.mkdirs()) {
				throw new RuntimeException("The working directory could not be created: " + target.getAbsolutePath());
			}
		}
		try {
			if (server) {
				String mcDownload = SERVER_DOWNLOADS.get(mcversion);
				if (mcDownload == null) {
					System.err.println("No downloads for minecraft server " + mcversion);
					return;
				}
				File mcJar = new File(target, "minecraftborge-" + mcversion + ".jar");
				if (mcJar.exists()) {
					System.err.println("Minecraft JAR already exists!");
					return;
				}
				if (!mcJar.createNewFile()) {
					System.err.println("Could not create JAR!");
					return;
				}

				byte[] pkt = new byte[-Short.MIN_VALUE];
				int n;
				InputStream in;
				OutputStream out;
				System.out.println("Downloading minecraft server JAR...");
				timer = System.currentTimeMillis();
				in = new URL(mcDownload).openStream();
				out = Files.newOutputStream(mcJar.toPath());

				while ((n = in.read(pkt)) != -1) {
					out.write(pkt, 0, n);
				}

				in.close();
				out.close();
				System.out.println("Done! (" + (System.currentTimeMillis() - timer) + "ms)");

				System.out.println("Downloading asm-9.9 JAR...");
				timer = System.currentTimeMillis();
				in = getASMStream();
				out = Files.newOutputStream(new File(target, "asm-9.9.jar").toPath());

				while ((n = in.read(pkt)) != -1) {
					out.write(pkt, 0, n);
				}

				in.close();
				out.close();
				System.out.println("Done! (" + (System.currentTimeMillis() - timer) + "ms)");

				System.out.println("Downloading MinecraftBorge server JAR...");
				timer = System.currentTimeMillis();
				in = new URL(formatURL(mcversion, borgeversion, "server")).openStream();
				out = Files.newOutputStream(new File(target, "MinecraftBorge.jar").toPath());

				while ((n = in.read(pkt)) != -1) {
					out.write(pkt, 0, n);
				}

				in.close();
				out.close();
				System.out.println("Done! (" + (System.currentTimeMillis() - timer) + "ms)");
			} else {
				String mcDownload = CLIENT_DOWNLOADS.get(mcversion);
				if (mcDownload == null) {
					System.err.println("No downloads for minecraft client " + mcversion);
					return;
				}
				File mcJar = new File(target, "minecraftborge-" + mcversion + ".jar");
				if (mcJar.exists()) {
					System.err.println("Minecraft JAR already exists!");
					return;
				}
				if (!mcJar.createNewFile()) {
					System.err.println("Could not create JAR!");
					return;
				}

				byte[] pkt = new byte[-Short.MIN_VALUE];
				int n;
				InputStream in;
				OutputStream out;
				System.out.println("Downloading Minecraft client JAR...");
				timer = System.currentTimeMillis();
				in = new URL(mcDownload).openStream();
				out = Files.newOutputStream(mcJar.toPath());

				while ((n = in.read(pkt)) != -1) {
					out.write(pkt, 0, n);
				}

				in.close();
				out.close();
				System.out.println("Done! (" + (System.currentTimeMillis() - timer) + "ms)");

				System.out.println("Downloading asm-9.9 JAR...");
				timer = System.currentTimeMillis();
				in = getASMStream();
				out = Files.newOutputStream(new File(target, "asm-9.9.jar").toPath());

				while ((n = in.read(pkt)) != -1) {
					out.write(pkt, 0, n);
				}

				in.close();
				out.close();
				System.out.println("Done! (" + (System.currentTimeMillis() - timer) + "ms)");

				System.out.println("Downloading MinecraftBorge client JAR...");
				timer = System.currentTimeMillis();
				in = new URL(formatURL(mcversion, borgeversion, "client")).openStream();
				out = Files.newOutputStream(new File(target, "MinecraftBorge.jar").toPath());

				while ((n = in.read(pkt)) != -1) {
					out.write(pkt, 0, n);
				}

				in.close();
				out.close();
				System.out.println("Done! (" + (System.currentTimeMillis() - timer) + "ms)");
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to download resources", e);
		}
		try {
			System.out.println("Installing MinecraftBorge into JAR");
			timer = System.currentTimeMillis();

			byte[] pkt = new byte[-Short.MIN_VALUE];
			int n;

			JarFile mcJar = new JarFile(new File(target, "minecraftborge-" + mcversion + ".jar"));
			JarFile asmJar = new JarFile(new File(target, "asm-9.9.jar"));
			JarFile borgeJar = new JarFile(new File(target, "MinecraftBorge.jar"));
			File installed = new File(target, "minecraftborge-" + mcversion + ".jar_tmp");
			if (!installed.createNewFile()) throw new IOException("The installation JAR could not be created");
			InputStream in;
			JarOutputStream out = new JarOutputStream(Files.newOutputStream(installed.toPath()));

			Enumeration<JarEntry> mcJarEntries = mcJar.entries();

			while (mcJarEntries.hasMoreElements()) {
				JarEntry mcJarEntry = mcJarEntries.nextElement();
				JarEntry borgeJarEntry = borgeJar.getJarEntry(mcJarEntry.getName());
				if (borgeJarEntry == null) {
					JarEntry entry = new JarEntry(mcJarEntry.getName());
					out.putNextEntry(entry);
					in = mcJar.getInputStream(mcJarEntry);
					while ((n = in.read(pkt)) != -1) {
						out.write(pkt, 0, n);
					}
					in.close();
				}
			}

			Enumeration<JarEntry> asmJarEntries = asmJar.entries();

			while (asmJarEntries.hasMoreElements()) {
				JarEntry asmJarEntry = asmJarEntries.nextElement();
				JarEntry entry = new JarEntry(asmJarEntry.getName());

				out.putNextEntry(entry);
				in = asmJar.getInputStream(asmJarEntry);
				while ((n = in.read(pkt)) != -1) {
					out.write(pkt, 0, n);
				}
				in.close();
			}

			Enumeration<JarEntry> borgeJarEntries = borgeJar.entries();

			while (borgeJarEntries.hasMoreElements()) {
				JarEntry borgeJarEntry = borgeJarEntries.nextElement();
				JarEntry entry = new JarEntry(borgeJarEntry.getName());

				out.putNextEntry(entry);
				in = borgeJar.getInputStream(borgeJarEntry);
				while ((n = in.read(pkt)) != -1) {
					out.write(pkt, 0, n);
				}
				in.close();
			}

			mcJar.close();
			asmJar.close();
			borgeJar.close();
			out.close();

			File origin = new File(target, "minecraftborge-" + mcversion + ".jar");
			origin.delete();
			installed.renameTo(origin);

			if (!server) {
				File jsonData = new File(target, "minecraftborge-" + mcversion + ".json");
				jsonData.createNewFile();
				try (InputStream stream = CLASS_LOADER.getResourceAsStream("version_json/" + mcversion + ".json");
				     OutputStream os = Files.newOutputStream(jsonData.toPath())) {
					while ((n = stream.read(pkt)) != -1) {
						os.write(pkt, 0, n);
					}
				}
			}

			System.out.println("Done! (" + (System.currentTimeMillis() - timer) + "ms)");
		} catch (Exception e) {
			throw new RuntimeException("Failed to install mod loader", e);
		}
		target = targetOld;
	}

	public static File getAppDir(String var0) {
		String var1 = System.getProperty("user.home", ".");
		File var2;
		switch(getOSId()) {
			case 1:
			case 2:
				var2 = new File(var1, '.' + var0 + '/');
				break;
			case 3:
				String var3 = System.getenv("APPDATA");
				if(var3 != null) {
					var2 = new File(var3, "." + var0 + '/');
				} else {
					var2 = new File(var1, '.' + var0 + '/');
				}
				break;
			case 4:
				var2 = new File(var1, "Library/Application Support/" + var0);
				break;
			default:
				var2 = new File(var1, var0 + '/');
		}

		if(!var2.exists() && !var2.mkdirs()) {
			throw new RuntimeException("The working directory could not be created: " + var2);
		} else {
			return var2;
		}
	}

	public static int getOSId() {
		String os = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
		if (os.contains("win")) return 3;
		if (os.contains("solaris") || os.contains("sunos")) return 2;
		if (os.contains("linux") || os.contains("unix")) return 1;
		if (os.contains("macos") || os.contains("ios")) return 4;
		return 0;
	}
}
