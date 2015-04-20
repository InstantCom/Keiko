package net.instantcom.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public abstract class HumanReadable {

    private static final DecimalFormat df =
        new DecimalFormat("###,###,##0.0", DecimalFormatSymbols
            .getInstance(Locale.US));
    private static final DecimalFormat dfRatio =
        new DecimalFormat("###,###,##0.000", DecimalFormatSymbols
            .getInstance(Locale.US));

    private static final long KILO = 1024L;
    private static final long MEGA = 1024L * KILO;
    private static final long GIGA = 1024L * MEGA;
    private static final long TERA = 1024L * GIGA;
    private static final long PETA = 1024L * TERA;

    public static String humanReadableBytes(long bytes) {
        double value = bytes;
        StringBuffer sb = new StringBuffer();
        String suffix = null;
        if (value < KILO) {
            suffix = "bytes";
        } else if (value < MEGA) {
            value /= KILO;
            suffix = "KB";
        } else if (value < GIGA) {
            value /= MEGA;
            suffix = "MB";
        } else if (value < TERA) {
            value /= GIGA;
            suffix = "GB";
        } else if (value < PETA) {
            value /= TERA;
            suffix = "TB";
        } else {
            value /= PETA;
            suffix = "PB";
        }
        sb.append(df.format(value));
        sb.append(' ');
        sb.append(suffix);
        return sb.toString();
    }

    public static String humanReadableRatio(double ratio) {
        return dfRatio.format(ratio);
    }

}
