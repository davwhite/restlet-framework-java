/**
 * Copyright 2005-2014 Restlet
 * 
 * The contents of this file are subject to the terms of one of the following
 * open source licenses: Apache 2.0 or LGPL 3.0 or LGPL 2.1 or CDDL 1.0 or EPL
 * 1.0 (the "Licenses"). You can select the license that you prefer but you may
 * not use this file except in compliance with one of these Licenses.
 * 
 * You can obtain a copy of the Apache 2.0 license at
 * http://www.opensource.org/licenses/apache-2.0
 * 
 * You can obtain a copy of the LGPL 3.0 license at
 * http://www.opensource.org/licenses/lgpl-3.0
 * 
 * You can obtain a copy of the LGPL 2.1 license at
 * http://www.opensource.org/licenses/lgpl-2.1
 * 
 * You can obtain a copy of the CDDL 1.0 license at
 * http://www.opensource.org/licenses/cddl1
 * 
 * You can obtain a copy of the EPL 1.0 license at
 * http://www.opensource.org/licenses/eclipse-1.0
 * 
 * See the Licenses for the specific language governing permissions and
 * limitations under the Licenses.
 * 
 * Alternatively, you can obtain a royalty free commercial license with less
 * limitations, transferable or non-transferable, directly at
 * http://restlet.com/products/restlet-framework
 * 
 * Restlet is a registered trademark of Restlet S.A.S.
 */

package org.restlet.ext.apispark.info;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.restlet.Application;
import org.restlet.Component;
import org.restlet.Context;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.Restlet;
import org.restlet.Server;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Protocol;
import org.restlet.data.Reference;
import org.restlet.data.Status;
import org.restlet.engine.Engine;
import org.restlet.representation.Representation;
import org.restlet.representation.Variant;
import org.restlet.resource.Directory;
import org.restlet.resource.Finder;
import org.restlet.resource.ServerResource;
import org.restlet.routing.Filter;
import org.restlet.routing.Route;
import org.restlet.routing.Router;
import org.restlet.routing.TemplateRoute;
import org.restlet.routing.VirtualHost;

/**
 * APISpark enabled application. This {@link Application} subclass can describe
 * itself in APISpark by introspecting its content. You can obtain this
 * representation with an OPTIONS request addressed exactly to the application
 * URI (e.g. "http://host:port/path/to/application"). By default, the returned
 * representation gleans the list of all attached {@link ServerResource} classes
 * and calls {@link #getName()} to get the title and {@link #getDescription()}
 * the textual content of the APISpark document generated. This default behavior
 * can be customized by overriding the
 * {@link #getApplicationInfo(Request, Response)} method.<br>
 * <br>
 * In case you want to customize the XSLT stylesheet, you can override the
 * {@link #createAPISparkRepresentation(ApplicationInfo)} method and return an
 * instance of an {@link ApisparkRepresentation} subclass overriding the
 * {@link ApisparkRepresentation#getHtmlRepresentation()} method.<br>
 * <br>
 * In addition, this class can create an instance and configure it with an
 * user-provided APISpark/XML document. In this case, it creates a root
 * {@link Router} and for each resource found in the APISpark document, it tries
 * to attach a {@link ServerResource} class to the router using its APISpark
 * path. For this, it looks up the qualified name of the {@link ServerResource}
 * subclass using the APISpark's "id" attribute of the "resource" elements. This
 * is the only Restlet specific convention on the original APISpark document.<br>
 * <br>
 * To attach an application configured in this way to an existing component, you
 * can call the {@link #attachToComponent(Component)} or the
 * {@link #attachToHost(VirtualHost)} methods. In this case, it uses the "base"
 * attribute of the APISpark "resources" element as the URI attachment path to
 * the virtual host.<br>
 * <br>
 * Concurrency note: instances of this class or its subclasses can be invoked by
 * several threads at the same time and therefore must be thread-safe. You
 * should be especially careful when storing state in member variables. <br>
 * 
 * @author Jerome Louvel
 */
public class ApisparkApplication extends Application {

    /**
     * Indicates if the application should be automatically described via
     * APISpark when an OPTIONS request handles a "*" target URI.
     */
    private volatile boolean autoDescribing;

    /** The APISpark base reference. */
    private volatile Reference baseRef;

    /** The router to {@link ServerResource} classes. */
    private volatile Router router;

    /**
     * Creates an application that can automatically introspect and expose
     * itself as with a APISpark description upon reception of an OPTIONS
     * request on the "*" target URI.
     */
    public ApisparkApplication() {
        this((Context) null);
    }

    /**
     * Creates an application that can automatically introspect and expose
     * itself as with a APISpark description upon reception of an OPTIONS
     * request on the "*" target URI.
     * 
     * @param context
     *            The context to use based on parent component context. This
     *            context should be created using the
     *            {@link Context#createChildContext()} method to ensure a proper
     *            isolation with the other applications.
     */
    public ApisparkApplication(Context context) {
        super(context);
        this.autoDescribing = true;
    }

    /**
     * Creates an application described using a APISpark document. Creates a
     * router where Resource classes are attached and set it as the root
     * Restlet.
     * 
     * By default the application is not automatically described. If you want
     * to, you can call {@link #setAutoDescribing(boolean)}.
     * 
     * @param context
     *            The context to use based on parent component context. This
     *            context should be created using the
     *            {@link Context#createChildContext()} method to ensure a proper
     *            isolation with the other applications.
     * @param apispark
     *            The APISpark description document.
     */
    public ApisparkApplication(Context context, Representation apispark) {
        super(context);
        this.autoDescribing = false;

        try {
            // Instantiates a APISparkRepresentation of the APISpark document
            ApisparkRepresentation apisparkRep = null;

            if (apispark instanceof ApisparkRepresentation) {
                apisparkRep = (ApisparkRepresentation) apispark;
            } else {
                // TODO to be done
                // apisparkRep = new APISparkRepresentation(apispark);
            }

            final Router root = new Router(getContext());
            this.router = root;
            setInboundRoot(root);

            if (apisparkRep.getApplication() != null) {
                if (apisparkRep.getApplication().getResources() != null) {
                    for (final ResourceInfo resource : apisparkRep
                            .getApplication().getResources().getResources()) {
                        attachResource(resource, null, this.router);
                    }

                    // Analyzes the APISpark resources base
                    setBaseRef(apisparkRep.getApplication().getResources()
                            .getBaseRef());
                }

                // Set the name of the application as the title of the first
                // documentation tag.
                if (!apisparkRep.getApplication().getDocumentations().isEmpty()) {
                    setName(apisparkRep.getApplication().getDocumentations()
                            .get(0).getTitle());
                }
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING,
                    "Error during the attachment of the APISpark application",
                    e);
        }
    }

    /**
     * Creates an application described using a APISpark document. Creates a
     * router where Resource classes are attached and set it as the root
     * Restlet.
     * 
     * By default the application is not automatically described. If you want
     * to, you can call {@link #setAutoDescribing(boolean)}.
     * 
     * @param apispark
     *            The APISpark description document.
     */
    public ApisparkApplication(Representation apispark) {
        this(null, apispark);
    }

    /**
     * Adds the necessary server connectors to the component.
     * 
     * @param component
     *            The parent component to update.
     */
    private void addConnectors(Component component) {
        // Create the server connector
        Protocol protocol = getBaseRef().getSchemeProtocol();
        int port = getBaseRef().getHostPort();
        boolean exists = false;

        if (port == -1) {
            for (Server server : component.getServers()) {
                if (server.getProtocols().contains(protocol)
                        && (server.getPort() == protocol.getDefaultPort())) {
                    exists = true;
                }
            }

            if (!exists) {
                component.getServers().add(protocol);
            }
        } else {
            for (Server server : component.getServers()) {
                if (server.getProtocols().contains(protocol)
                        && (server.getPort() == port)) {
                    exists = true;
                }
            }

            if (!exists) {
                component.getServers().add(protocol, port);
            }
        }
    }

    /**
     * Represents the resource as a APISpark description.
     * 
     * @param request
     *            The current request.
     * @param response
     *            The current response.
     * @return The APISpark description.
     */
    protected Representation apisparkRepresent(Request request,
            Response response) {
        return apisparkRepresent(getPreferredAPISparkVariant(request), request,
                response);
    }

    /**
     * Represents the resource as a APISpark description for the given variant.
     * 
     * @param variant
     *            The APISpark variant.
     * @param request
     *            The current request.
     * @param response
     *            The current response.
     * @return The APISpark description.
     */
    protected Representation apisparkRepresent(Variant variant,
            Request request, Response response) {
        Representation result = null;

        if (variant != null) {
            ApplicationInfo applicationInfo = getApplicationInfo(request,
                    response);
            DocumentationInfo doc = null;

            if ((getName() != null) && !"".equals(getName())) {
                if (applicationInfo.getDocumentations().isEmpty()) {
                    doc = new DocumentationInfo();
                    applicationInfo.getDocumentations().add(doc);
                } else {
                    doc = applicationInfo.getDocumentations().get(0);
                }

                doc.setTitle(getName());
            }

            if ((doc != null) && (getDescription() != null)
                    && !"".equals(getDescription())) {
                doc.setTextContent(getDescription());
            }

            if (MediaType.APPLICATION_JSON.equals(variant.getMediaType())) {
                result = createAPISparkRepresentation(applicationInfo);
            }
        }

        return result;
    }

    /**
     * Attaches a resource, as specified in a APISpark document, to a specified
     * router, then recursively attaches its child resources.
     * 
     * @param currentResource
     *            The resource to attach.
     * @param parentResource
     *            The parent resource. Needed to correctly resolve the "path" of
     *            the resource. Should be null if the resource is root-level.
     * @param router
     *            The router to which to attach the resource and its children.
     * @throws ClassNotFoundException
     *             If the class name specified in the "id" attribute of the
     *             resource does not exist, this exception will be thrown.
     */
    private void attachResource(ResourceInfo currentResource,
            ResourceInfo parentResource, Router router)
            throws ClassNotFoundException {

        String uriPattern = currentResource.getPath();

        // If there is a parentResource, add its uriPattern to this one
        if (parentResource != null) {
            String parentUriPattern = parentResource.getPath();

            if ((parentUriPattern.endsWith("/") == false)
                    && (uriPattern.startsWith("/") == false)) {
                parentUriPattern += "/";
            }

            uriPattern = parentUriPattern + uriPattern;
            currentResource.setPath(uriPattern);
        } else if (!uriPattern.startsWith("/")) {
            uriPattern = "/" + uriPattern;
            currentResource.setPath(uriPattern);
        }

        Finder finder = createFinder(router, uriPattern, currentResource);

        if (finder != null) {
            // Attach the resource itself
            router.attach(uriPattern, finder);
        }

        // Attach children of the resource
        for (ResourceInfo childResource : currentResource.getChildResources()) {
            attachResource(childResource, currentResource, router);
        }
    }

    /**
     * Attaches the application to the given component if the application has a
     * APISpark base reference. The application will be attached to an existing
     * virtual host if possible, otherwise a new one will be created.
     * 
     * @param component
     *            The parent component to update.
     * @return The parent virtual host.
     */
    public VirtualHost attachToComponent(Component component) {
        VirtualHost result = null;

        if (getBaseRef() != null) {
            // Create the virtual host
            result = getVirtualHost(component);

            // Attach the application to the virtual host
            attachToHost(result);

            // Adds the necessary server connectors
            addConnectors(component);
        } else {
            getLogger()
                    .warning(
                            "The APISpark application has no base reference defined. Unable to guess the virtual host.");
        }

        return result;
    }

    /**
     * Attaches the application to the given host using the APISpark base
     * reference.
     * 
     * @param host
     *            The virtual host to attach to.
     */
    public void attachToHost(VirtualHost host) {
        if (getBaseRef() != null) {
            final String path = getBaseRef().getPath();
            if (path == null) {
                host.attach("", this);
            } else {
                host.attach(path, this);
            }

        } else {
            getLogger()
                    .warning(
                            "The APISpark application has no base reference defined. Unable to guess the virtual host.");
        }
    }

    /**
     * Indicates if the application and all its resources can be described using
     * APISpark.
     * 
     * @param remainingPart
     *            The URI remaining part.
     * @param request
     *            The request to handle.
     * @param response
     *            The response to update.
     */
    protected boolean canDescribe(String remainingPart, Request request,
            Response response) {
        return isAutoDescribing()
                && Method.OPTIONS.equals(request.getMethod())
                && (response.getStatus().isClientError() || !response
                        .isEntityAvailable())
                && ("/".equals(remainingPart) || "".equals(remainingPart));
    }

    /**
     * Creates a new APISpark representation for a given {@link ApplicationInfo}
     * instance describing an application.
     * 
     * @param applicationInfo
     *            The application description.
     * @return The created {@link ApisparkRepresentation}.
     */
    protected Representation createAPISparkRepresentation(
            ApplicationInfo applicationInfo) {
        return new ApisparkRepresentation(applicationInfo);
    }

    /**
     * Creates a finder for the given resource info. By default, it looks up for
     * an "id" attribute containing a fully qualified class name.
     * 
     * @param router
     *            The parent router.
     * @param resourceInfo
     *            The APISpark resource descriptor.
     * @return The created finder.
     * @throws ClassNotFoundException
     */
    @SuppressWarnings("unchecked")
    protected Finder createFinder(Router router, String uriPattern,
            ResourceInfo resourceInfo) throws ClassNotFoundException {
        Finder result = null;

        if (resourceInfo.getIdentifier() != null) {
            // The "id" attribute conveys the target class name
            Class<? extends ServerResource> targetClass = (Class<? extends ServerResource>) Engine
                    .loadClass(resourceInfo.getIdentifier());
            result = router.createFinder(targetClass);
        } else {
            getLogger()
                    .fine("Unable to find the 'id' attribute of the resource element with this path attribute \""
                            + uriPattern + "\"");
        }

        return result;
    }

    /**
     * Returns the available APISpark variants.
     * 
     * @return The available APISpark variants.
     */
    protected List<Variant> getAPISparkVariants() {
        final List<Variant> result = new ArrayList<Variant>();
        result.add(new Variant(MediaType.APPLICATION_JSON));
        result.add(new Variant(MediaType.APPLICATION_XML));
        result.add(new Variant(MediaType.TEXT_XML));
        return result;
    }

    /**
     * Returns a APISpark description of the current application. By default,
     * this method discovers all the resources attached to this application. It
     * can be overridden to add documentation, list of representations, etc.
     * 
     * @param request
     *            The current request.
     * @param response
     *            The current response.
     * @return An application description.
     */
    protected ApplicationInfo getApplicationInfo(Request request,
            Response response) {
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.getResources().setBaseRef(
                request.getResourceRef().getBaseRef());
        applicationInfo.getResources().setResources(
                getResourceInfos(applicationInfo,
                        getNextRouter(getInboundRoot()), request, response));
        return applicationInfo;
    }

    /**
     * Returns the APISpark base reference.
     * 
     * @return The APISpark base reference.
     */
    public Reference getBaseRef() {
        return this.baseRef;
    }

    /**
     * Returns the next router available.
     * 
     * @param current
     *            The current Restlet to inspect.
     * @return The first router available.
     */
    private Router getNextRouter(Restlet current) {
        Router result = getRouter();

        if (result == null) {
            if (current instanceof Router) {
                result = (Router) current;
            } else if (current instanceof Filter) {
                result = getNextRouter(((Filter) current).getNext());
            }
        }

        return result;
    }

    /**
     * Returns the preferred APISpark variant according to the client
     * preferences specified in the request.
     * 
     * @param request
     *            The request including client preferences.
     * @return The preferred APISpark variant.
     */
    protected Variant getPreferredAPISparkVariant(Request request) {
        return getConnegService().getPreferredVariant(getAPISparkVariants(),
                request, getMetadataService());
    }

    /**
     * Completes the data available about a given Filter instance.
     * 
     * @param applicationInfo
     *            The parent application.
     * @param filter
     *            The Filter instance to document.
     * @param path
     *            The base path.
     * @param request
     *            The current request.
     * @param response
     *            The current response.
     * @return The resource description.
     */
    private ResourceInfo getResourceInfo(ApplicationInfo applicationInfo,
            Filter filter, String path, Request request, Response response) {
        return getResourceInfo(applicationInfo, filter.getNext(), path,
                request, response);
    }

    /**
     * Completes the data available about a given Finder instance.
     * 
     * @param applicationInfo
     *            The parent application.
     * @param resourceInfo
     *            The ResourceInfo object to complete.
     * @param finder
     *            The Finder instance to document.
     * @param request
     *            The current request.
     * @param response
     *            The current response.
     */
    private ResourceInfo getResourceInfo(ApplicationInfo applicationInfo,
            Finder finder, String path, Request request, Response response) {
        ResourceInfo result = null;
        Object resource = null;

        // Save the current application
        Application.setCurrent(this);

        if (finder instanceof Directory) {
            resource = finder;
        } else {
            // The handler instance targeted by this finder.
            ServerResource sr = finder.find(request, response);

            if (sr != null) {
                sr.init(getContext(), request, response);
                sr.updateAllowedMethods();
                resource = sr;
            }
        }

        if (resource != null) {
            result = new ResourceInfo();
            ResourceInfo.describe(applicationInfo, result, resource, path);
        }

        return result;
    }

    /**
     * Completes the data available about a given Restlet instance.
     * 
     * @param applicationInfo
     *            The parent application.
     * @param resourceInfo
     *            The ResourceInfo object to complete.
     * @param restlet
     *            The Restlet instance to document.
     * @param request
     *            The current request.
     * @param response
     *            The current response.
     */
    private ResourceInfo getResourceInfo(ApplicationInfo applicationInfo,
            Restlet restlet, String path, Request request, Response response) {
        ResourceInfo result = null;

        if (restlet instanceof ApisparkDescribable) {
            result = ((ApisparkDescribable) restlet)
                    .getResourceInfo(applicationInfo);
            result.setPath(path);
        } else if (restlet instanceof Finder) {
            result = getResourceInfo(applicationInfo, (Finder) restlet, path,
                    request, response);
        } else if (restlet instanceof Router) {
            result = new ResourceInfo();
            result.setPath(path);
            result.setChildResources(getResourceInfos(applicationInfo,
                    (Router) restlet, request, response));
        } else if (restlet instanceof Filter) {
            result = getResourceInfo(applicationInfo, (Filter) restlet, path,
                    request, response);
        }

        return result;
    }

    /**
     * Returns the APISpark data about the given Route instance.
     * 
     * @param applicationInfo
     *            The parent application.
     * @param route
     *            The Route instance to document.
     * @param basePath
     *            The base path.
     * @param request
     *            The current request.
     * @param response
     *            The current response.
     * @return The APISpark data about the given Route instance.
     */
    private ResourceInfo getResourceInfo(ApplicationInfo applicationInfo,
            Route route, String basePath, Request request, Response response) {
        ResourceInfo result = null;

        if (route instanceof TemplateRoute) {
            TemplateRoute templateRoute = (TemplateRoute) route;
            String path = templateRoute.getTemplate().getPattern();

            // APISpark requires resource paths to be relative to parent path
            if (path.startsWith("/") && basePath.endsWith("/")) {
                path = path.substring(1);
            }

            result = getResourceInfo(applicationInfo, route.getNext(), path,
                    request, response);
        }

        return result;
    }

    /**
     * Completes the list of ResourceInfo instances for the given Router
     * instance.
     * 
     * @param applicationInfo
     *            The parent application.
     * @param router
     *            The router to document.
     * @param request
     *            The current request.
     * @param response
     *            The current response.
     * @return The list of ResourceInfo instances to complete.
     */
    private List<ResourceInfo> getResourceInfos(
            ApplicationInfo applicationInfo, Router router, Request request,
            Response response) {
        List<ResourceInfo> result = new ArrayList<ResourceInfo>();

        for (Route route : router.getRoutes()) {
            ResourceInfo resourceInfo = getResourceInfo(applicationInfo, route,
                    "/", request, response);

            if (resourceInfo != null) {
                result.add(resourceInfo);
            }
        }

        if (router.getDefaultRoute() != null) {
            ResourceInfo resourceInfo = getResourceInfo(applicationInfo,
                    router.getDefaultRoute(), "/", request, response);
            if (resourceInfo != null) {
                result.add(resourceInfo);
            }
        }

        return result;
    }

    /**
     * Returns the router where the {@link ServerResource} classes created from
     * the APISpark description document are attached.
     * 
     * @return The root router.
     */
    public Router getRouter() {
        return this.router;
    }

    /**
     * Returns the virtual host matching the APISpark application's base
     * reference. Creates a new one and attaches it to the component if
     * necessary.
     * 
     * @param component
     *            The parent component.
     * @return The related virtual host.
     */
    private VirtualHost getVirtualHost(Component component) {
        // Create the virtual host if necessary
        final String hostDomain = this.baseRef.getHostDomain();
        final String hostPort = Integer.toString(this.baseRef.getHostPort());
        final String hostScheme = this.baseRef.getScheme();

        VirtualHost host = null;
        for (final VirtualHost vh : component.getHosts()) {
            if (vh.getHostDomain().equals(hostDomain)
                    && vh.getHostPort().equals(hostPort)
                    && vh.getHostScheme().equals(hostScheme)) {
                host = vh;
            }
        }

        if (host == null) {
            // A new virtual host needs to be created
            host = new VirtualHost(component.getContext().createChildContext());
            host.setHostDomain(hostDomain);
            host.setHostPort(hostPort);
            host.setHostScheme(hostScheme);
            component.getHosts().add(host);
        }

        return host;
    }

    /**
     * Handles the requests normally in all cases then handles the special case
     * of the OPTIONS requests that exactly target the application. In this
     * case, the application is automatically introspected and described as a
     * APISpark representation based on the result of the
     * {@link #getApplicationInfo(Request, Response)} method.<br>
     * The automatic introspection happens only if the request hasn't already
     * been successfully handled. That is to say, it lets users provide their
     * own handling of OPTIONS requests.
     * 
     * @param request
     *            The request to handle.
     * @param response
     *            The response to update.
     */
    @Override
    public void handle(Request request, Response response) {
        // Preserve the resource reference.
        Reference rr = request.getResourceRef().clone();

        // Do the regular handling
        super.handle(request, response);

        // Restore the resource reference
        request.setResourceRef(rr);

        // Handle OPTIONS requests.
        String rp = rr.getRemainingPart(false, false);

        if (canDescribe(rp, request, response)) {
            // Make sure that the base of the "resources" element ends with a
            // "/".
            if (!rr.getBaseRef().getIdentifier().endsWith("/")) {
                rr.setBaseRef(rr.getBaseRef() + "/");
            }

            // Returns a APISpark representation of the application.
            response.setEntity(apisparkRepresent(request, response));

            if (response.isEntityAvailable()) {
                response.setStatus(Status.SUCCESS_OK);
            }
        }
    }

    /**
     * Indicates if the application should be automatically described via
     * APISpark when an OPTIONS request handles a "*" target URI.
     * 
     * @return True if the application should be automatically described via
     *         APISpark.
     */
    public boolean isAutoDescribing() {
        return autoDescribing;
    }

    /**
     * Indicates if the application should be automatically described via
     * APISpark when an OPTIONS request handles a "*" target URI.
     * 
     * @param autoDescribed
     *            True if the application should be automatically described via
     *            APISpark.
     */
    public void setAutoDescribing(boolean autoDescribed) {
        this.autoDescribing = autoDescribed;
    }

    /**
     * Sets the APISpark base reference.
     * 
     * @param baseRef
     *            The APISpark base reference.
     */
    public void setBaseRef(Reference baseRef) {
        this.baseRef = baseRef;
    }

}