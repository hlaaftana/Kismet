package hlaaftana.kismet.lib

import groovy.transform.CompileStatic
import hlaaftana.kismet.vm.IKismetObject

@CompileStatic
class IteratorIterable<T> implements Iterator<T>, Iterable<T>, IKismetObject<IteratorIterable<T>> {
	Iterator<T> inside

	IteratorIterable(Iterator<T> inside) {
		this.inside = inside
	}

	@Override
	Iterator<T> iterator() {
		inside
	}

	@Override
	boolean hasNext() {
		inside.hasNext()
	}

	@Override
	T next() {
		inside.next()
	}

	@Override
	void remove() {
		inside.remove()
	}

	IteratorIterable<T> inner() {
		this
	}
}
