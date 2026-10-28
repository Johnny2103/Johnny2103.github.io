# Nova Galerie – R8 rules. Libraries ship their own consumer rules; these are only extras.
-keepattributes Signature, *Annotation*, SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
