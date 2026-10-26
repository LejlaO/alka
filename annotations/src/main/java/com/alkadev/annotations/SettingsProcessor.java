package com.alkadev.annotations;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@SupportedAnnotationTypes("com.alkadev.annotations.AlkaSettings")
@SupportedOptions("alka.templateOut")
public class SettingsProcessor extends AbstractProcessor {
    private static final String RESOURCE = "META-INF/alka/settings.properties";
    private boolean written;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        List<TypeElement> found = new ArrayList<>();
        for (Element element : round.getElementsAnnotatedWith(AlkaSettings.class))
            if (element instanceof TypeElement type) found.add(type);
        if (found.isEmpty()) return false;
        if (found.size() > 1 || written) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Only one class per module may carry @AlkaSettings (one jar, one settings file).", found.get(found.size() - 1));
            return false;
        }

        TypeElement type = found.get(0);
        AlkaSettings settings = type.getAnnotation(AlkaSettings.class);
        String kind = kind(type);
        if (!settings.managerType().isBlank() && !kind.equals("manager"))
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "managerType belongs on a class that extends ObjectManager.", type);
        if (settings.usesManagerTypes().length > 0 && !kind.equals("plugin"))
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "usesManagerTypes belongs on a class that extends ObjectPlugin.", type);
        String text = render(type, settings);
        try {
            FileObject out = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", RESOURCE, type);
            try (Writer w = out.openWriter()) {
                w.write(text);
            }
            String copy = processingEnv.getOptions().get("alka.templateOut");
            if (copy != null && !copy.isBlank()) {
                Path target = Path.of(copy).normalize();
                Files.createDirectories(target.getParent());
                Files.writeString(target, text, StandardCharsets.UTF_8);
                processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Alka settings template written to " + target);
            }
            written = true;
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Could not write the settings template: " + e, type);
        }
        return false;
    }

    private String render(TypeElement type, AlkaSettings settings) {
        StringBuilder out = new StringBuilder();
        out.append("# Alka settings template, generated at compile time from @AlkaSettings. Do not edit: change the annotation and rebuild.\n");
        out.append("# component: ").append(type.getQualifiedName()).append('\n');
        out.append("# kind: ").append(kind(type)).append('\n');
        out.append("# api-version: ").append(Names.API_VERSION).append('\n');
        if (!settings.managerType().isBlank()) out.append("# manager-type: ").append(settings.managerType().trim().toUpperCase()).append('\n');
        if (settings.usesManagerTypes().length > 0) {
            List<String> types = new ArrayList<>();
            for (String t : settings.usesManagerTypes()) if (!t.isBlank()) types.add(t.trim().toUpperCase());
            if (!types.isEmpty()) out.append("# uses-manager-types: ").append(String.join(", ", types)).append('\n');
        }
        if (!settings.description().isBlank()) out.append("# description: ").append(one(settings.description())).append('\n');
        out.append("# Every setting below is optional unless marked required. To use one in a real settings file, copy its last line without the leading #.\n");
        for (Setting s : settings.value()) {
            out.append('\n');
            out.append("## @param ").append(s.key()).append('\n');
            if (!s.description().isBlank()) out.append("## description: ").append(one(s.description())).append('\n');
            out.append("## type: ").append(s.type().name().toLowerCase()).append('\n');
            if (s.required()) out.append("## required: true\n");
            out.append("## default: ").append(one(s.defaultValue())).append('\n');
            if (s.type() == SettingType.LIST && s.options().length > 0) out.append("## options: ").append(String.join(", ", s.options())).append('\n');
            if (s.type() == SettingType.NUMBER) {
                if (!s.min().isBlank()) out.append("## min: ").append(s.min()).append('\n');
                if (!s.max().isBlank()) out.append("## max: ").append(s.max()).append('\n');
            }
            out.append('#').append(s.key()).append('=').append(one(s.defaultValue())).append('\n');
        }
        return out.toString();
    }

    // plugin and manager are found by their base class names, so this module needs no dependency on the engine
    private String kind(TypeElement type) {
        for (javax.lang.model.type.TypeMirror t = type.getSuperclass(); t.getKind() == javax.lang.model.type.TypeKind.DECLARED; ) {
            TypeElement parent = (TypeElement) processingEnv.getTypeUtils().asElement(t);
            String name = parent.getQualifiedName().toString();
            if (name.equals("com.alkadev.utils.objectplugin.ObjectPlugin")) return "plugin";
            if (name.equals("com.alkadev.utils.objectmanager.ObjectManager")) return "manager";
            t = parent.getSuperclass();
        }
        return "service";
    }

    private static String one(String text) {
        return text.replace("\r", " ").replace("\n", " ").trim();
    }
}
