package hlaaftana.kismet.vm

import groovy.transform.CompileStatic

@CompileStatic
@SuppressWarnings("GroovyUnusedDeclaration")
class KismetTuple implements IKismetObject<Tuple<IKismetObject>>, List<IKismetObject> {
	IKismetObject[] inner

	KismetTuple(int length) { this(new IKismetObject[length]) }

	KismetTuple(IKismetObject[] inner) { this.inner = inner }

	Tuple<IKismetObject> inner() { new Tuple(inner) }

	int size() { inner.length }

	boolean isEmpty() { inner.length == 0 }

	boolean contains(Object o) {
		for (final e : inner) if (o == e) return true
		false
	}

	Iterator<IKismetObject> iterator() {
		Arrays.stream(inner).iterator()
	}

	Object[] toArray() { inner }

	def <T> T[] toArray(T[] a) {
		System.arraycopy(inner, 0, a, 0, inner.length)
		a
	}

	boolean add(IKismetObject iKismetObject) { throw new UnsupportedOperationException('Cannot add to tuple with length ' + inner.length) }

	boolean remove(Object o) {
		throw new UnsupportedOperationException('Cannot remove object from tuple with length ' + inner.length)
	}

	boolean containsAll(Collection<?> c) {
		for (e in c) if (!contains(e)) return false
		true
	}

	boolean addAll(Collection<? extends IKismetObject> c) {
		throw new UnsupportedOperationException('Cannot add to tuple with length ' + inner.length)
	}

	boolean addAll(int index, Collection<? extends IKismetObject> c) {
		throw new UnsupportedOperationException('Cannot add to tuple with length ' + inner.length)
	}

	boolean removeAll(Collection<?> c) {
		throw new UnsupportedOperationException('Cannot remove on tuple with length ' + inner.length)
	}

	boolean retainAll(Collection<?> c) {
		throw new UnsupportedOperationException('Cannot retain on tuple with length ' + inner.length)
	}

	void clear() {
		throw new UnsupportedOperationException('Cannot clear tuple with length ' + inner.length)
	}

	IKismetObject get(int i) { inner[i] }

	@Override
	IKismetObject set(int index, IKismetObject element) {
		inner[index] = element
	}

	void add(int index, IKismetObject element) {
		throw new UnsupportedOperationException('Cannot add to tuple with length ' + inner.length)
	}

	IKismetObject remove(int index) {
		throw new UnsupportedOperationException('Cannot remove from tuple with length ' + inner.length)
	}

	int indexOf(Object o) {
		for (int i = 0; i < inner.length; ++i) if (o == inner[i]) return i
		-1
	}

	int lastIndexOf(Object o) {
		for (int i = inner.length - 1; i >= 0; --i) if (o == inner[i]) return i
		-1
	}

	ListIterator<IKismetObject> listIterator() {
		Arrays.asList(inner).listIterator()
	}

	ListIterator<IKismetObject> listIterator(int index) {
		Arrays.asList(inner).listIterator(index)
	}

	List<IKismetObject> subList(int fromIndex, int toIndex) {
		Arrays.asList(inner).subList(fromIndex, toIndex)
	}

	String toString() {
		def builder = new StringBuilder(inner.toString())
		builder.setCharAt(0, (char) '(')
		builder.setCharAt(builder.length() - 1, (char) ')')
		builder.toString()
	}

	int hashCode() {
		Arrays.hashCode(inner)
	}

	boolean equals(obj) {
		if (obj !instanceof KismetTuple || ((KismetTuple) obj).size() != size()) return false
		for (int i = 0; i < inner.length; ++i)
			if (get(i) != ((KismetTuple) obj).get(i)) return false
		true
	}

	def asType(Class type) { inner().asType(type) }
}
