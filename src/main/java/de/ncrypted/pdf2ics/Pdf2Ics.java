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
  private static String curFrom = null;
  private static String curTo = null;
  private static String curKind = null;
  private static String curTitle = null;
  private static String curTopic = null;
  private static Calendar calendar = null;
  private static TimeZoneRegistry registry;
  private static TimeZone timezone;
  public static final boolean DEBUG = true;

  static {
    System.setProperty("net.fortuna.ical4j.timezone.cache.impl", MapTimeZoneCache.class.getName());
    registry = TimeZoneRegistryFactory.getInstance().createRegistry();
    timezone = registry.getTimeZone("Europe/Berlin");
  }

  public static void main(String[] args) {
    // fetch target pdf
    String pdfPath;
    if (!DEBUG) {
      if (args.length < 1) {
        System.err.println("Usage: java -jar pdf2ics.jar path/to/calendar.pdf");
        return;
      }
      pdfPath = args[0];
    } else {
      pdfPath = "calendar.pdf";
    }
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
      textStripper.setSortByPosition(true);
      textStripper.setStartPage(0);
      textStripper.setEndPage(pdDocument.getNumberOfPages());
      String text = textStripper.getText(pdDocument);
      String[] lines = text.split("\n");
      for (String line : lines) {
        float endXOfStart = Float.valueOf(line.split("%%%")[0]);
        List<String> words =
            Arrays.stream(line.split("%%%")[1].split("\\$\\$\\$")).map(String::trim)
                .collect(Collectors.toList());
        words = words.subList(0, words.size() - 1);
        System.out.println("[DEBUG] " + words);
        System.out.println("at " + endXOfStart);
        if (isDay(endXOfStart)) {
          parseIntoIcs(words, 0);
        } else if (isTime(endXOfStart)) {
          parseIntoIcs(words, 1);
        } else if (isOtherLecturer(endXOfStart)) {
          parseIntoIcs(words, 2);
        } else {
          System.out.println(
              "[INFO] ignoring new line with x-start at " + endXOfStart + ": " + words);
        }
      }
      pdDocument.close();

      // save calendar
      FileOutputStream fout = new FileOutputStream("calendar.ics");
      CalendarOutputter outputter = new CalendarOutputter();
      outputter.output(calendar, fout);
      fout.close();
    } catch (IOException | ParseException e) {
      e.printStackTrace();
    }
  }

  private static void parseIntoIcs(List<String> info, int lineKind) throws ParseException {
    // line starts with date
    if (lineKind == 0) {
      // split at space (ascii 32/0x20) or nbsp (ascii 160/0xA0)
      String[] dateInfos = info.get(0).split("[\\x20|\\xA0]");
      // should contain weekday and date
      if (dateInfos.length == 2) {
        curDate = dateInfos[1];
      } else {
        System.out.println("[INFO] matched invalid date based on positioning in string '" + info
            + "'. Skipping and using remaining information.");
      }
      parseIntoIcs(info.subList(1, info.size()), 1);
      // line starts with time
    } else if (lineKind == 1) {
      curFrom = info.get(0);
      curTo = info.size() > 1 ? info.get(1) : null;
      curKind = info.size() > 2 ? info.get(2) : null;
      curTitle = info.size() > 3 ? info.get(3) : null;
      curTopic = info.size() > 4 ? info.get(4) : null;
      DateTime startDate = new DateTime(curDate + " " + info.get(0), "dd.MM.yy HH:mm", timezone);
      DateTime endDate = new DateTime(curDate + " " + info.get(1), "dd.MM.yy HH:mm", timezone);
      VEvent event = new VEvent(startDate, endDate, info.get(3));

      // add uid
      UidGenerator ug = new RandomUidGenerator();
      Uid uid = ug.generateUid();
      event.getProperties().add(uid);

      // add description
      String description = info.get(2) + "\n";
      for (int i = 4; i < info.size(); i++) {
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
      // line starts with other lecturer
    } else if (lineKind == 2) {
      DateTime startDate = new DateTime(curDate + " " + curFrom, "dd.MM.yy HH:mm", timezone);
      DateTime endDate = new DateTime(curDate + " " + curTo, "dd.MM.yy HH:mm", timezone);
      VEvent event = new VEvent(startDate, endDate, curTitle);

      // add uid
      UidGenerator ug = new RandomUidGenerator();
      Uid uid = ug.generateUid();
      event.getProperties().add(uid);

      // add description
      String description = curKind + "\n" + curTopic + "\n";
      for (String word : info) {
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
    return 109 < pos && pos < 113;
  }

  private static boolean isTime(float pos) {
    return 155 < pos && pos < 159;
  }

  private static boolean isOtherLecturer(float pos) {
    return 400 < pos && pos < 500;
  }
}
