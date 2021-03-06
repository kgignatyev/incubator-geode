package com.gemstone.gemfire.internal.logging;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Defines the common date format for GemFire and provides DateFormat instances.
 * 
 * @author Kirk Lund
 */
public final class DateFormatter {

  /**
   * The format string used to format the timestamp of GemFire log messages
   */
  public final static String FORMAT_STRING = "yyyy/MM/dd HH:mm:ss.SSS z";
  
  private final static DateFormat timeFormatter = createDateFormat();
  
  /**
   * Creates a SimpleDateFormat using {@link #FORMAT_STRING}.
   * 
   * Thread Safety Issue: (From SimpleDateFormat)
   * Date formats are not synchronized.
   * It is recommended to create separate format instances for each thread.
   * If multiple threads access a format concurrently, it must be synchronized
   * externally.
   */
  public static DateFormat createDateFormat() {
    return new SimpleDateFormat(DateFormatter.FORMAT_STRING);
  }

  /**
   * Creates a SimpleDateFormat using specified formatString.
   */
  public static DateFormat createDateFormat(final String formatString) {
    return new SimpleDateFormat(formatString);
  }
  
  /**
   * Gets a String representation of the current time.
   * @return a String representation of the current time.
   */
  public static String getTimeStamp() {
    return formatDate(new Date());
  }
  /**
   * Convert a Date to a timestamp String.
   * @param d a Date to format as a timestamp String.
   * @return a String representation of the current time.
   */
  public static String formatDate(Date d) {
    try {
      synchronized (timeFormatter) {
        // Need sync: see bug 21858
        return timeFormatter.format(d);
      }
    } catch (Exception e1) {
      // Fix bug 21857
      try {
        return d.toString();
      } catch (Exception e2) {
        try {
          return Long.toString(d.getTime());
        } catch (Exception e3) {
          return "timestampFormatFailed";
        }
      }
    }
  }
  
  /**
   * Do not instantiate this class.
   */
  private DateFormatter() {
  }
}
