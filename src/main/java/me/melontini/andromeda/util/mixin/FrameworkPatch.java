package me.melontini.andromeda.util.mixin;

import lombok.CustomLog;
import lombok.SneakyThrows;
import me.melontini.dark_matter.api.danger.instrumentation.InstrumentationAccess;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.spongepowered.asm.mixin.throwables.MixinError;

@CustomLog
public class FrameworkPatch {

    @SneakyThrows
    public static void patch() {
        if (InstrumentationAccess.canInstrument()) {
            LOGGER.info("Definitely up to a lot of good");
            Class<?> cls = Class.forName("org.spongepowered.asm.mixin.transformer.MixinApplicatorStandard");
            InstrumentationAccess.retransform(classNode -> {
                MethodNode node = classNode.methods.stream().filter(mn -> "apply".equals(mn.name)).findFirst().orElse(null);
                if (node == null) {
                    LOGGER.warn("Failed to find 'apply' method in class " + classNode.name);
                    return classNode;
                }

                final String mixinError = Type.getInternalName(MixinError.class);
                TryCatchBlockNode ourTryCatch = node.tryCatchBlocks.stream().filter(tryCatchBlockNode -> mixinError.equals(tryCatchBlockNode.type)).findFirst().orElse(null);
                if (ourTryCatch != null) {
                    LOGGER.warn("Required try-catch block already exists in class " + classNode.name);
                    return classNode;
                }

                TryCatchBlockNode tryCatch = node.tryCatchBlocks.stream().filter(tryCatchBlockNode -> "java/lang/Exception".equals(tryCatchBlockNode.type)).findFirst().orElse(null);
                if (tryCatch == null) {
                    LOGGER.warn("Failed to find try-catch block in class " + classNode.name);
                    return classNode;
                }

                //https://github.com/SpongePowered/Mixin/pull/640
                node.tryCatchBlocks.add(new TryCatchBlockNode(tryCatch.start, tryCatch.end, tryCatch.handler, mixinError));

                return classNode;
            }, cls);
        }
    }
}