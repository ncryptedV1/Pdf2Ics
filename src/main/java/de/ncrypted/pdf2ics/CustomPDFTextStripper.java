package de.ncrypted.pdf2ics;

import java.io.IOException;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

public class CustomPDFTextStripper extends PDFTextStripper {
  boolean startOfLine = true;

  public CustomPDFTextStripper() throws IOException {
    super();
  }

  @Override
  protected void startPage(PDPage page) throws IOException {
    startOfLine = true;
    super.startPage(page);
  }

  @Override
  protected void writeLineSeparator() throws IOException {
    startOfLine = true;
    super.writeLineSeparator();
  }

  @Override
  protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
    if (startOfLine) {
      TextPosition lastPosition = textPositions.get(textPositions.size() - 1);
      writeString((lastPosition.getXDirAdj() + lastPosition.getWidthDirAdj()) + "%%%");
      startOfLine = false;
    }
    writeString(text + "$$$");
  }
}
