package io.ray.serve.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

public class ReflectUtil {

  @SuppressWarnings("rawtypes")
  public static Class[] getParameterTypes(Object[] parameters) {
    Class[] parameterTypes = null;
    if (ArrayUtils.isEmpty(parameters)) {
      return null;
    }
    parameterTypes = new Class[parameters.length];
    for (int i = 0; i < parameters.length; i++) {
      parameterTypes[i] = parameters[i].getClass();
    }
    return parameterTypes;
  }

  @SuppressWarnings("rawtypes")
  public static Constructor getConstructor(Class targetClass, Object[] parameters)
      throws NoSuchMethodException {
    Class[] parameterTypes = getParameterTypes(parameters);
    Constructor[] constructors = targetClass.getConstructors();
    for (Constructor constructor : constructors) {
      if (assignable(parameterTypes, constructor.getParameterTypes())) {
        return constructor;
      }
    }
    throw new NoSuchMethodException(
        targetClass.getName() + ".<init>" + argumentTypesToString(parameterTypes));
  }

  @SuppressWarnings("rawtypes")
  public static Method getMethod(Class targetClass, String name, Object... parameters)
      throws NoSuchMethodException {
    Class[] parameterTypes = getParameterTypes(parameters);
    Method[] methods = targetClass.getMethods();
    Method result = null;
    for (Method method : methods) {
      if (StringUtils.equals(name, method.getName())
          && assignable(parameterTypes, method.getParameterTypes()) && (result == null
              || assignable(method.getParameterTypes(), result.getParameterTypes()))) {
        result = method;
      }
    }
    if (result == null) {
      throw new NoSuchMethodException(
          targetClass.getName() + "." + name + argumentTypesToString(parameterTypes));
    }
    return result;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static boolean assignable(Class[] from, Class[] to) {
    if (from == null) {
      return to == null || to.length == 0;
    }

    if (to == null) {
      return from.length == 0;
    }

    if (from.length != to.length) {
      return false;
    }

    for (int i = 0; i < from.length; i++) {
      if (!to[i].isAssignableFrom(from[i])) {
        return false;
      }
    }

    return true;
  }

  private static String argumentTypesToString(Class<?>[] argTypes) {
    StringBuilder buf = new StringBuilder();
    buf.append("(");
    if (argTypes != null) {
      for (int i = 0; i < argTypes.length; i++) {
        if (i > 0) {
          buf.append(", ");
        }
        Class<?> c = argTypes[i];
        buf.append((c == null) ? "null" : c.getName());
      }
    }
    buf.append(")");
    return buf.toString();
  }

  @SuppressWarnings("rawtypes")
  public static List<String> getMethodStrings(Class targetClass) {
    if (targetClass == null) {
      return null;
    }
    Method[] methods = targetClass.getMethods();
    if (methods == null || methods.length == 0) {
      return null;
    }
    List<String> methodStrings = new ArrayList<>();
    for (Method method : methods) {
      methodStrings.add(method.toString());
    }
    return methodStrings;
  }

}
