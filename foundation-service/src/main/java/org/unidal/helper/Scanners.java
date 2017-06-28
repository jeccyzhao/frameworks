package org.unidal.helper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Scanners {
   public static DirScanner forDir() {
      return DirScanner.INSTANCE;
   }

   public static JarScanner forJar() {
      return JarScanner.INSTANCE;
   }

   public static ResourceScanner forResource() {
      return ResourceScanner.INSTANCE;
   }

   public static abstract class DirMatcher implements IMatcher<File> {
      @Override
      public boolean isDirEligible() {
         return true;
      }

      @Override
      public boolean isFileElegible() {
         return false;
      }
   }

   public enum DirScanner {
      INSTANCE;

      public List<File> scan(File base, IMatcher<File> matcher) {
         List<File> files = new ArrayList<File>();
         StringBuilder relativePath = new StringBuilder();

         scanForFiles(base, relativePath, matcher, false, files);

         return files;
      }

      private void scanForFiles(File base, StringBuilder relativePath, IMatcher<File> matcher, boolean foundFirst,
            List<File> files) {
         int len = relativePath.length();
         File dir = len == 0 ? base : new File(base, relativePath.toString());
         String[] list = dir.list();

         if (list != null) {
            for (String item : list) {
               File child = new File(dir, item);

               if (len > 0) {
                  relativePath.append('/');
               }

               relativePath.append(item);

               if (matcher.isDirEligible() && child.isDirectory()) {
                  IMatcher.Direction direction = matcher.matches(base, relativePath.toString());

                  switch (direction) {
                  case MATCHED:
                     files.add(child);
                     break;
                  case DOWN:
                     // for sub-folders
                     scanForFiles(base, relativePath, matcher, foundFirst, files);
                     break;
                  default:
                     break;
                  }
               } else if (matcher.isFileElegible()) {
                  IMatcher.Direction direction = matcher.matches(base, relativePath.toString());

                  switch (direction) {
                  case MATCHED:
                     if (child.isFile()) {
                        files.add(child);
                     }
                     break;
                  case DOWN:
                     if (child.isDirectory()) {
                        // for sub-folders
                        scanForFiles(base, relativePath, matcher, foundFirst, files);
                     }
                     break;
                  default:
                     break;
                  }
               }

               relativePath.setLength(len); // reset

               if (foundFirst && files.size() > 0) {
                  break;
               }
            }
         }
      }

      public File scanForOne(File base, IMatcher<File> matcher) {
         List<File> files = new ArrayList<File>(1);
         StringBuilder relativePath = new StringBuilder();

         scanForFiles(base, relativePath, matcher, true, files);

         if (files.isEmpty()) {
            return null;
         } else {
            return files.get(0);
         }
      }
   }

   public static abstract class FileMatcher implements IMatcher<File> {
      @Override
      public boolean isDirEligible() {
         return false;
      }

      @Override
      public boolean isFileElegible() {
         return true;
      }
   }

   public static interface IMatcher<T> {
      public boolean isDirEligible();

      public boolean isFileElegible();

      public Direction matches(T base, String path);

      public enum Direction {
         MATCHED,

         DOWN,

         NEXT;

         public boolean isDown() {
            return this == DOWN;
         }

         public boolean isMatched() {
            return this == MATCHED;
         }

         public boolean isNext() {
            return this == NEXT;
         }
      }
   }

   public enum JarScanner {
      INSTANCE;

      public ZipEntry getEntry(String jarFileName, String name) {
         ZipFile zipFile = null;

         try {
            zipFile = new ZipFile(jarFileName);

            ZipEntry entry = zipFile.getEntry(name);

            return entry;
         } catch (IOException e1) {
            // ignore
         } finally {
            if (zipFile != null) {
               try {
                  zipFile.close();
               } catch (IOException e) {
                  // ignore it
               }
            }
         }

         return null;
      }

      public byte[] getEntryContent(String jarFileName, String entryPath) {
         byte[] bytes = null;
         ZipFile zipFile = null;

         try {
            zipFile = new ZipFile(jarFileName);
            ZipEntry entry = zipFile.getEntry(entryPath);

            if (entry != null) {
               InputStream inputStream = zipFile.getInputStream(entry);
               bytes = Files.forIO().readFrom(inputStream);
            }
         } catch (Exception e) {
            // ignore
         } finally {
            if (zipFile != null) {
               try {
                  zipFile.close();
               } catch (Exception e) {
               }
            }
         }

         return bytes;
      }

      public boolean hasEntry(String jarFileName, String name) {
         return getEntry(jarFileName, name) != null;
      }

      public List<String> scan(File base, IMatcher<File> matcher) {
         List<String> files = new ArrayList<String>();
         scanForFiles(base, matcher, false, files);

         return files;
      }

      public List<String> scan(ZipFile zipFile, IMatcher<ZipEntry> matcher) {
         List<String> files = new ArrayList<String>();
         scanForEntries(zipFile, matcher, false, files);

         return files;
      }

      private void scanForEntries(ZipFile zipFile, IMatcher<ZipEntry> matcher, boolean foundFirst, List<String> names) {
         Enumeration<? extends ZipEntry> entries = zipFile.entries();

         while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();

            if (matcher.isDirEligible() && entry.isDirectory()) {
               IMatcher.Direction direction = matcher.matches(entry, name);

               if (direction.isMatched()) {
                  names.add(name);
               }
            } else if (matcher.isFileElegible() && !entry.isDirectory()) {
               IMatcher.Direction direction = matcher.matches(entry, name);

               if (direction.isMatched()) {
                  names.add(name);
               }
            }

            if (foundFirst && names.size() > 0) {
               break;
            }
         }
      }

      private void scanForFiles(File jarFile, IMatcher<File> matcher, boolean foundFirst, List<String> names) {
         ZipFile zipFile = null;

         try {
            zipFile = new ZipFile(jarFile);
         } catch (IOException e) {
            // ignore it
         }

         if (zipFile != null) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {
               ZipEntry entry = entries.nextElement();
               String name = entry.getName();

               if (matcher.isDirEligible() && entry.isDirectory()) {
                  IMatcher.Direction direction = matcher.matches(jarFile, name);

                  if (direction.isMatched()) {
                     names.add(name);
                  }
               } else if (matcher.isFileElegible() && !entry.isDirectory()) {
                  IMatcher.Direction direction = matcher.matches(jarFile, name);

                  if (direction.isMatched()) {
                     names.add(name);
                  }
               }

               if (foundFirst && names.size() > 0) {
                  break;
               }
            }
         }
      }

      public String scanForOne(File jarFile, IMatcher<File> matcher) {
         List<String> files = new ArrayList<String>(1);

         scanForFiles(jarFile, matcher, true, files);

         if (files.isEmpty()) {
            return null;
         } else {
            return files.get(0);
         }
      }
   }

   public static abstract class ResourceMatcher implements IMatcher<URL> {
      @Override
      public boolean isDirEligible() {
         return false;
      }

      @Override
      public boolean isFileElegible() {
         return true;
      }

      public abstract Direction matches(URL url, String path);
   }

   public enum ResourceScanner {
      INSTANCE;

      @SuppressWarnings("deprecation")
      private String decode(String url) {
         try {
            return URLDecoder.decode(url, "utf-8");
         } catch (UnsupportedEncodingException e) {
            return URLDecoder.decode(url);
         }
      }

      public List<URL> scanAll(String resourceBase, final ResourceMatcher matcher) throws IOException {
         Enumeration<URL> resources = getClass().getClassLoader().getResources(resourceBase);
         final List<URL> urls = new ArrayList<URL>();

         while (resources.hasMoreElements()) {
            final URL url = resources.nextElement();
            String protocol = url.getProtocol();

            if ("file".equals(protocol)) {
               DirScanner.INSTANCE.scan(new File(decode(url.getPath())), new FileMatcher() {
                  @Override
                  public Direction matches(File base, String path) {
                     try {
                        URL u = new URL(url, path);
                        Direction d = matcher.matches(u, path);

                        if (d.isMatched()) {
                           urls.add(u);
                        }

                        return d;
                     } catch (MalformedURLException e) {
                        // ignore it
                     }

                     return Direction.DOWN;
                  }
               });
            } else if ("jar".equals(protocol)) {
               URL u = new URL(url.getPath());

               if ("file".equals(u.getProtocol())) {
                  String path = u.getPath();
                  int pos = path.indexOf('!');
                  File file = new File(decode(path.substring(0, pos)));
                  final String base = path.substring(pos + 2);

                  JarScanner.INSTANCE.scan(new ZipFile(file), new ZipEntryMatcher() {
                     @Override
                     public Direction matches(ZipEntry entry, String path) {
                        if (path.startsWith(base)) {
                           try {
                              String p = path.substring(base.length());
                              URL u = new URL(url, p);
                              Direction d = matcher.matches(u, p);

                              if (d.isMatched()) {
                                 urls.add(u);
                              }

                              return d;
                           } catch (MalformedURLException e) {
                              // ignore it
                           }
                        }

                        return Direction.DOWN;
                     }
                  });
               }
            }
         }

         return urls;
      }
   }

   public static abstract class ZipEntryMatcher implements IMatcher<ZipEntry> {
      @Override
      public boolean isDirEligible() {
         return false;
      }

      @Override
      public boolean isFileElegible() {
         return true;
      }

      public abstract Direction matches(ZipEntry entry, String path);
   }
}
