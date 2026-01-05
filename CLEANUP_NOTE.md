IMPORTANT WHEN UPDATING AN EXISTING REPO
---------------------------------------
If you are copying these files into an existing Git repository, you MUST DELETE the old file:

app/src/main/java/com/example/collage/CollageTemplates.kt

Older versions had CollageTemplates defined in two places (CollageTemplate.kt + CollageTemplates.kt),
which causes a compilation error (Redeclaration: object CollageTemplates).

This ZIP does NOT include CollageTemplates.kt — but Git will keep your old copy unless you delete it.
