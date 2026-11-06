package atf.rentz.governance;

import atf.core.files.AtfJavaSourceReferences;
import java.util.*;
import java.util.regex.Pattern;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.io.IOException;

final class SourceLayoutRules {
    private static final Pattern PACKAGE=Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern IMPORT=Pattern.compile("\\bimport\\s+(static\\s+)?([\\w$]+(?:\\s*\\.\\s*(?:[\\w$]+|\\*))*)\\s*;");
    private SourceLayoutRules(){}
    static List<String> inspect(String relative,String source,List<String> forbidden){
        var result=new ArrayList<String>();String code=AtfJavaSourceReferences.codeOnly(source);var pkg=PACKAGE.matcher(code);
        if(!pkg.find())result.add("GOV-PACKAGE-MISSING");
        else if(!relative.substring(0,Math.max(0,relative.lastIndexOf('/'))).equals(pkg.group(1).replace('.','/')))result.add("GOV-PACKAGE-PATH");
        var imports=IMPORT.matcher(code);var seen=new HashSet<String>();
        while(imports.find())if(!seen.add((imports.group(1)==null?"":"static ")+imports.group(2).replaceAll("\\s+","")))result.add("GOV-DUPLICATE-IMPORT");
        for(String name:forbidden)if(AtfJavaSourceReferences.containsNamespace(source,name))result.add("GOV-BOUNDARY:"+name);
        return result;
    }
    static boolean oversized(List<?> files,int limit){
        if(limit!=8)throw new IllegalArgumentException("The inherited cohesion limit is eight; no package waivers");
        return files.size()>limit;
    }
    static List<Path> uncovered(Path project,List<Path> roots,List<Path> excluded)throws IOException{
        var result=new ArrayList<Path>();
        Files.walkFileTree(project,new SimpleFileVisitor<>(){
            @Override public FileVisitResult preVisitDirectory(Path directory,BasicFileAttributes attributes){
                return excluded.stream().anyMatch(directory::startsWith)?FileVisitResult.SKIP_SUBTREE:FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file,BasicFileAttributes attributes){
                if(file.toString().endsWith(".java")&&roots.stream().noneMatch(file::startsWith))result.add(file);
                return FileVisitResult.CONTINUE;
            }
        });
        return result;
    }
}
