package com.lingm.vibekey;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

record ReleaseVersion(int major, int minor, int patch, int developmentBuild)
        implements Comparable<ReleaseVersion> {
    private static final String NUMBER = "(0|[1-9][0-9]{0,8})";
    private static final Pattern FORMAT = Pattern.compile(
            NUMBER + "\\." + NUMBER + "\\." + NUMBER + "(?:-dev(?:\\.([1-9][0-9]{0,8}))?)?");

    static ReleaseVersion parse(String name) {
        Matcher match = FORMAT.matcher(name);
        if (!match.matches()) return null;
        int developmentBuild = !name.contains("-dev") ? Integer.MAX_VALUE
                : match.group(4) == null ? 0 : Integer.parseInt(match.group(4));
        return new ReleaseVersion(Integer.parseInt(match.group(1)), Integer.parseInt(match.group(2)),
                Integer.parseInt(match.group(3)), developmentBuild);
    }

    @Override public int compareTo(ReleaseVersion other) {
        int result = Integer.compare(major, other.major);
        if (result == 0) result = Integer.compare(minor, other.minor);
        if (result == 0) result = Integer.compare(patch, other.patch);
        if (result == 0) result = Integer.compare(developmentBuild, other.developmentBuild);
        return result;
    }
}
