package dev.mtropt;

/**
 * Доступ к приватным полям org.mtr.mod.resource.CachedResource.
 * Интерфейс добавляется к классу MTR миксином CachedResourceMixin.
 */
public interface CachedResourceAccess {

	Object mtropt$getData();

	void mtropt$setData(Object data);

	long mtropt$getExpiry();

	void mtropt$setExpiry(long expiry);

	/** Тип данных ресурса, определяется один раз: 0 = ещё не определён, 1 = цепочка/GPU-модель поезда, 2 = прочее. */
	int mtropt$getKind();

	void mtropt$setKind(int kind);
}
