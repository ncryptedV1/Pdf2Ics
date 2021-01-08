package de.ncrypted.pdf2ics;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.TimeZoneRegistryFactory;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.property.CalScale;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Version;
import net.fortuna.ical4j.util.MapTimeZoneCache;
import net.fortuna.ical4j.util.RandomUidGenerator;
import net.fortuna.ical4j.util.UidGenerator;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

public class Pdf2Ics {

  private static String curDate = null;
  private static Calendar calendar = null;
  private static TimeZoneRegistry registry;
  private static TimeZone timezone;
  private static VTimeZone tz;

  static {
    System.setProperty("net.fortuna.ical4j.timezone.cache.impl", MapTimeZoneCache.class.getName());
    registry = TimeZoneRegistryFactory.getInstance().createRegistry();
    timezone = registry.getTimeZone("Europe/Berlin");
    tz = timezone.getVTimeZone();
  }

  public static void main(String[] args) {
    if (args.length < 1) {
      System.err.println("Usage: java -jar pdf2ics.jar path/to/calendar.pdf");
      return;
    }
    String pdfPath = args[0];
    File pdfFile = new File(pdfPath);
    if (!pdfFile.exists()) {
      System.err.println("File " + pdfFile.getAbsolutePath() + " doesn't exist!");
      return;
    }

    // init ics
    calendar = new Calendar();
    calendar.getProperties().add(new ProdId("-//Oliver Schirmer//pdf2ics//DE"));
    calendar.getProperties().add(Version.VERSION_2_0);
    calendar.getProperties().add(CalScale.GREGORIAN);

    // parse pdf into ics
    try {
      PDDocument pdDocument = PDDocument.load(pdfFile);
      PDFTextStripper textStripper = new CustomPDFTextStripper();
      String text = textStripper.getText(pdDocument);
      String[] lines = text.split("\n");
      for (String line : lines) {
        Float endXOfStart = Float.valueOf(line.split("%%%")[0]);
        List<String> words =
            Arrays.asList(line.split("%%%")[1].split("\\$\\$\\$")).stream().map(String::trim)
                .collect(Collectors.toList());
        words = words.subList(0, words.size() - 1);
        if (isDay(endXOfStart)) {
          parseIntoIcs(words);
        } else if (isTime(endXOfStart)) {
          parseIntoIcs(words);
        }
      }
      pdDocument.close();

      // save calendar
      FileOutputStream fout = new FileOutputStream("calendar.ics");
      CalendarOutputter outputter = new CalendarOutputter();
      outputter.output(calendar, fout);
      fout.close();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (ParseException e) {
      e.printStackTrace();
    }
  }

  private static void parseIntoIcs(List<String> info) throws ParseException {
    if (info.get(0).matches("[a-zA-Z].*")) {
      curDate = info.get(0).split(" ")[1];
      parseIntoIcs(info.subList(1, info.size()));
    } else if (info.get(0).matches("\\d.*")) {
      DateTime startDate = new DateTime(curDate + " " + info.get(0), "dd.MM.yy HH:mm", timezone);
      DateTime endDate = new DateTime(curDate + " " + info.get(1), "dd.MM.yy HH:mm", timezone);
      VEvent event = new VEvent(startDate, endDate, info.get(2));

      // add uid
      UidGenerator ug = new RandomUidGenerator();
      Uid uid = ug.generateUid();
      event.getProperties().add(uid);

      // add description
      String description = "";
      for (int i = 3; i < info.size(); i++) {
        String word = info.get(i);
        if (word.toLowerCase().contains("leer") && word.length() < 10) {
          continue;
        }
        description += word + "\n";
      }
      if (description.length() != 0) {
        description = description.substring(0, description.length() - 1);
        event.getProperties().add(new Description(description));
      }

      calendar.getComponents().add(event);
    } else {
      System.err.println(
          "Couldn't parse into ics. Skipped following entry with len " + info.size() + ":");
      String oneLiner = "";
      for (String word : info) {
        oneLiner += word + " ";
      }
      System.err.println(oneLiner);
    }
  }

  private static boolean isDay(float pos) {
    return 51 < pos && pos < 61;
  }

  private static boolean isTime(float pos) {
    return 84 < pos && pos < 94;
  }
}
