/**
 * Copyright (c) 2011, Andre Steingress
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1.) Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 * 2.) Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 * 3.) Neither the name of Andre Steingress nor the names of its contributors may be used to endorse or
 * promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.gcontracts.generation;

import groovy.lang.*;

import java.beans.IntrospectionException;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom {@link MetaClass} implementation which intercepts all (static) method calls and tracks the
 * call stack. If a cyclic method call is detected (A calls B calls A), an exception is thrown.<p/>
 *
 * This class is primarily used to avoid cyclic method calls in assertions.
 *
 * @author ast
 */
public class CyclicMethodCallAwareMetaClass extends MetaClassImpl implements AdaptingMetaClass {

    protected MetaClass origMetaClass = null;

    private static ThreadLocal<Interceptor> interceptor = new ThreadLocal<Interceptor>() {
        @Override
        protected Interceptor initialValue() {
            return new CyclicAssertionCallInterceptor();
        }
    };

    private static ThreadLocal<Map<String, CyclicMethodCallAwareMetaClass>> metaClasses = new ThreadLocal<Map<String, CyclicMethodCallAwareMetaClass>>()  {
        @Override
        protected Map<String, CyclicMethodCallAwareMetaClass> initialValue() {
            return new HashMap<String, CyclicMethodCallAwareMetaClass>();
        }
    };

    public static CyclicMethodCallAwareMetaClass getProxy(GroovyObject theObject) throws IntrospectionException {

        synchronized (metaClasses.get())  {
            final MetaClassRegistry metaClassRegistry = GroovySystem.getMetaClassRegistry();
            final Map<String, CyclicMethodCallAwareMetaClass> metaClassMap = metaClasses.get();

            final MetaClass metaClass = theObject.getMetaClass();
            final Class theClass = metaClass.getTheClass();

            if (metaClassMap.containsKey(theClass.getName())) return metaClassMap.get(theClass.getName());

            metaClassMap.put(theClass.getName(), new CyclicMethodCallAwareMetaClass(metaClassRegistry, theClass, theObject));

            return metaClassMap.get(theClass.getName());
        }
    }

    public CyclicMethodCallAwareMetaClass(MetaClassRegistry registry, Class theClass, GroovyObject theObject) throws IntrospectionException {
        super(registry, theClass);
        super.initialize();

        origMetaClass = registry.getMetaClass(theClass);
        registry.setMetaClass(theClass, this);
        theObject.setMetaClass(this);
    }

    public void release()  {
        synchronized (metaClasses.get())  {
            final Map<String, CyclicMethodCallAwareMetaClass> metaClassMap = metaClasses.get();
            if (origMetaClass != null) {
                registry.setMetaClass(theClass, origMetaClass);
                metaClassMap.remove(theClass.getName());
            }
            // origMetaClass = null;
        }
    }

    /**
     * Call invokeMethod on adaptee with logic like in MetaClass unless we have an Interceptor.
     * With Interceptor the call is nested in its beforeInvoke and afterInvoke methods.
     * The method call is suppressed if Interceptor.doInvoke() returns false.
     * See Interceptor for details.
     */
    public Object invokeMethod(final Object object, final String methodName, final Object[] arguments) {
        return doCall(object, methodName, arguments, interceptor.get(), new Callable() {
            @Override
            public Object call() {
                return origMetaClass.invokeMethod(object, methodName, arguments);
            }
        });
    }

    /**
     * Call invokeStaticMethod on adaptee with logic like in MetaClass unless we have an Interceptor.
     * With Interceptor the call is nested in its beforeInvoke and afterInvoke methods.
     * The method call is suppressed if Interceptor.doInvoke() returns false.
     * See Interceptor for details.
     */
    public Object invokeStaticMethod(final Object object, final String methodName, final Object[] arguments) {
        return doCall(object, methodName, arguments, interceptor.get(), new Callable() {
            @Override
            public Object call() {
                return origMetaClass.invokeStaticMethod(object, methodName, arguments);
            }
        });
    }

    /**
     * Call invokeConstructor on adaptee with logic like in MetaClass unless we have an Interceptor.
     * With Interceptor the call is nested in its beforeInvoke and afterInvoke methods.
     * The method call is suppressed if Interceptor.doInvoke() returns false.
     * See Interceptor for details.
     */
    public Object invokeConstructor(final Object[] arguments) {
        return super.invokeConstructor(arguments);
    }

    /**
     * Interceptors the call to getProperty if a PropertyAccessInterceptor is
     * available
     *
     * @param object   the object to invoke the getter on
     * @param property the property name
     * @return the value of the property
     */
    public Object getProperty(Class aClass, Object object, String property, boolean b, boolean b1) {
        return super.getProperty(aClass, object, property, b, b1);
    }

    /**
     * Interceptors the call to a property setter if a PropertyAccessInterceptor
     * is available
     *
     * @param object   The object to invoke the setter on
     * @param property The property name to set
     * @param newValue The new value of the property
     */
    public void setProperty(Class aClass, Object object, String property, Object newValue, boolean b, boolean b1) {
        super.setProperty(aClass, object, property, newValue, b, b1);
    }

    @Override
    public MetaClass getAdaptee() {
        return origMetaClass;
    }

    @Override
    public void setAdaptee(MetaClass metaClass) {}

    // since Java has no Closures...
    private interface Callable {
        Object call();
    }

    private Object doCall(Object object, String methodName, Object[] arguments, Interceptor interceptor, Callable howToInvoke) {
        if (null == interceptor) {
            return howToInvoke.call();
        }
        Object result = interceptor.beforeInvoke(object, methodName, arguments);
        if (interceptor.doInvoke()) {
            try {
                result = howToInvoke.call();
            } finally {
                result = interceptor.afterInvoke(object, methodName, arguments, result);
            }
        }
        return result;
    }
}
