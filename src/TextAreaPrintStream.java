import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

public class TextAreaPrintStream extends PrintStream {
	private final JTextArea area;
	public TextAreaPrintStream(JTextArea area) {
		super(new Out(area));
		this.area = area;
	}

	@Override
	public void print(String s) {
		this.area.setText(this.area.getText() + s);
	}

	public static class Out extends OutputStream {
		private final JTextArea area;
		public Out(JTextArea area) {
			this.area = area;
		}

		@Override
		public void write(int b) throws IOException {
			this.area.setText(this.area.getText() + (char)b);
		}
	}
}
