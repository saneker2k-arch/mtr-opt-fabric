package dev.mtropt;

/**
 * Быстрая замена String.format для строк, в которых встречается только %s.
 * Результат идентичен String.format (для %s используется String.valueOf, как и в Formatter для обычных объектов).
 * Если в шаблоне есть что-то кроме %s (например %d, %%, %.2f) или аргументов не хватает,
 * вызывается настоящий String.format, поэтому поведение не меняется.
 */
public final class FastFormat {

	private FastFormat() {
	}

	public static String format(String format, Object[] args) {
		if (format == null || args == null || !OptConfig.get().fastStringFormat) {
			return String.format(format, args);
		}
		final int length = format.length();
		final StringBuilder builder = new StringBuilder(length + 24 * args.length);
		int argIndex = 0;
		for (int i = 0; i < length; i++) {
			final char c = format.charAt(i);
			if (c == '%') {
				if (i + 1 < length && format.charAt(i + 1) == 's' && argIndex < args.length) {
					builder.append(args[argIndex++]);
					i++;
				} else {
					return String.format(format, args);
				}
			} else {
				builder.append(c);
			}
		}
		return builder.toString();
	}
}
