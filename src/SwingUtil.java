import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.util.Enumeration;

public class SwingUtil {
	public static void applyGlobalFont(String family) {
		UIDefaults defaults = UIManager.getDefaults();
		Enumeration<Object> keys = defaults.keys();

		while (keys.hasMoreElements()) {
			Object key = keys.nextElement();
			Object value = defaults.get(key);
			if (value instanceof FontUIResource) {
				FontUIResource font = (FontUIResource) value;
				defaults.put(key, new FontUIResource(family, font.getStyle(), font.getSize()));
			}
		}
	}
}
