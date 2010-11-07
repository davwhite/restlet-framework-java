/**
 * Copyright 2005-2009 Noelios Technologies.
 * 
 * The contents of this file are subject to the terms of one of the following
 * open source licenses: LGPL 3.0 or LGPL 2.1 or CDDL 1.0 or EPL 1.0 (the
 * "Licenses"). You can select the license that you prefer but you may not use
 * this file except in compliance with one of these Licenses.
 * 
 * You can obtain a copy of the LGPL 3.0 license at
 * http://www.opensource.org/licenses/lgpl-3.0.html
 * 
 * You can obtain a copy of the LGPL 2.1 license at
 * http://www.opensource.org/licenses/lgpl-2.1.php
 * 
 * You can obtain a copy of the CDDL 1.0 license at
 * http://www.opensource.org/licenses/cddl1.php
 * 
 * You can obtain a copy of the EPL 1.0 license at
 * http://www.opensource.org/licenses/eclipse-1.0.php
 * 
 * See the Licenses for the specific language governing permissions and
 * limitations under the Licenses.
 * 
 * Alternatively, you can obtain a royalty free commercial license with less
 * limitations, transferable or non-transferable, directly at
 * http://www.noelios.com/products/restlet-engine
 * 
 * Restlet is a registered trademark of Noelios Technologies.
 */

package org.restlet.data;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.restlet.engine.util.DateUtils;
import org.restlet.representation.RepresentationInfo;

/**
 * Set of conditions applying to a request. This is equivalent to the HTTP
 * conditional headers.
 * 
 * @see <a
 *      href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.24"
 *      >If-Match</a>
 * @see <a
 *      href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.25"
 *      >If-Modified-Since</a>
 * @see <a
 *      href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.26"
 *      >If-None-Match</a>
 * @see <a
 *      href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.28"
 *      >If-Unmodified-Since</a>
 * 
 * @author Jerome Louvel
 */
public final class Conditions {
    /** The "if-match" condition */
    private volatile List<Tag> match;

    /** The "if-modified-since" condition */
    private volatile Date modifiedSince;

    /** The "if-none-match" condition */
    private volatile List<Tag> noneMatch;

    /** The "if-unmodified-since" condition */
    private volatile Date unmodifiedSince;

    /**
     * Constructor.
     */
    public Conditions() {
    }

    /**
     * Returns the modifiable list of tags that must be matched. Creates a new
     * instance if no one has been set.<br>
     * <br>
     * Note that when used with HTTP connectors, this property maps to the
     * "If-Match" header.
     * 
     * @return The "if-match" condition.
     */
    public List<Tag> getMatch() {
        // Lazy initialization with double-check.
        List<Tag> m = this.match;
        if (m == null) {
            synchronized (this) {
                m = this.match;
                if (m == null) {
                    this.match = m = new ArrayList<Tag>();
                }
            }
        }
        return m;
    }

    /**
     * Returns the condition based on the modification date of the requested
     * variant.<br>
     * <br>
     * Note that when used with HTTP connectors, this property maps to the
     * "If-Modified-Since" header.
     * 
     * @return The condition date.
     */
    public Date getModifiedSince() {
        return this.modifiedSince;
    }

    /**
     * Returns the modifiable list of tags that mustn't match. Creates a new
     * instance if no one has been set.<br>
     * <br>
     * Note that when used with HTTP connectors, this property maps to the
     * "If-None-Match" header.
     * 
     * @return The list of tags that mustn't match.
     */
    public List<Tag> getNoneMatch() {
        // Lazy initialization with double-check.
        List<Tag> n = this.noneMatch;
        if (n == null) {
            synchronized (this) {
                n = this.noneMatch;
                if (n == null) {
                    this.noneMatch = n = new ArrayList<Tag>();
                }
            }
        }
        return n;
    }

    /**
     * Returns the conditional status of a variant using a given method.
     * 
     * @param method
     *            The request method.
     * @param representationInfo
     *            The representation information that will be tested.
     * @return Null if the requested method can be performed, the status of the
     *         response otherwise.
     */
    public Status getStatus(Method method, RepresentationInfo representationInfo) {
        return getStatus(method, representationInfo != null,
                (representationInfo == null) ? null : representationInfo
                        .getTag(), (representationInfo == null) ? null
                        : representationInfo.getModificationDate());
    }

    /**
     * Returns the conditional status of a variant using a given method.
     * 
     * @param method
     *            The request method.
     * @param entityExists
     *            Indicates if the entity exists.
     * @param tag
     *            The tag.
     * @param modificationDate
     *            The modification date.
     * @return Null if the requested method can be performed, the status of the
     *         response otherwise.
     */
    public Status getStatus(Method method, boolean entityExists, Tag tag,
            Date modificationDate) {
        Status result = null;

        // Is the "if-Match" rule followed or not?
        if ((this.match != null) && !this.match.isEmpty()) {
            boolean matched = false;
            boolean failed = false;
            final boolean all = getMatch().get(0).equals(Tag.ALL);

            if (entityExists) {
                // If a tag exists
                if (!all && (tag != null)) {
                    // Check if it matches one of the representations already
                    // cached by the client
                    Tag matchTag;

                    for (final Iterator<Tag> iter = getMatch().iterator(); !matched
                            && iter.hasNext();) {
                        matchTag = iter.next();
                        matched = matchTag.equals(tag, false);
                    }
                } else {
                    matched = all;
                }
            } else {
                // See
                // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.24
                // If none of the entity tags match, or if "*" is given and no
                // current entity exists, the server MUST NOT perform the
                // requested method
                failed = all;
            }

            failed = failed || !matched;

            if (failed) {
                result = Status.CLIENT_ERROR_PRECONDITION_FAILED;
            }
        }

        // Is the "if-None-Match" rule followed or not?
        if ((result == null) && (this.noneMatch != null)
                && !this.noneMatch.isEmpty()) {
            boolean matched = false;

            if (entityExists) {
                // If a tag exists
                if (tag != null) {
                    // Check if it matches one of the representations
                    // already cached by the client
                    Tag noneMatchTag;

                    for (final Iterator<Tag> iter = getNoneMatch().iterator(); !matched
                            && iter.hasNext();) {
                        noneMatchTag = iter.next();
                        matched = noneMatchTag.equals(tag, (Method.GET
                                .equals(method) || Method.HEAD.equals(method)));
                    }

                    // The current representation matches one of those already
                    // cached by the client
                    if (matched) {
                        // Check if the current representation has been updated
                        // since the "if-modified-since" date. In this case, the
                        // rule is followed.
                        final Date modifiedSince = getModifiedSince();
                        final boolean isModifiedSince = (modifiedSince != null)
                                && (DateUtils.after(new Date(), modifiedSince)
                                        || (modificationDate == null) || DateUtils
                                        .after(modifiedSince, modificationDate));
                        matched = !isModifiedSince;
                    }
                }
            } else {
                matched = getNoneMatch().get(0).equals(Tag.ALL);
            }

            if (matched) {
                if (Method.GET.equals(method) || Method.HEAD.equals(method)) {
                    result = Status.REDIRECTION_NOT_MODIFIED;
                } else {
                    result = Status.CLIENT_ERROR_PRECONDITION_FAILED;
                }
            }
        }

        // Is the "if-Modified-Since" rule followed or not?
        if ((result == null) && (getModifiedSince() != null)) {
            final Date modifiedSince = getModifiedSince();
            final boolean isModifiedSince = (DateUtils.after(new Date(),
                    modifiedSince)
                    || (modificationDate == null) || DateUtils.after(
                    modifiedSince, modificationDate));

            if (!isModifiedSince) {
                if (Method.GET.equals(method) || Method.HEAD.equals(method)) {
                    result = Status.REDIRECTION_NOT_MODIFIED;
                } else {
                    result = Status.CLIENT_ERROR_PRECONDITION_FAILED;
                }
            }
        }

        // Is the "if-Unmodified-Since" rule followed or not?
        if ((result == null) && (getUnmodifiedSince() != null)) {
            final Date unModifiedSince = getUnmodifiedSince();
            final boolean isUnModifiedSince = ((unModifiedSince == null)
                    || (modificationDate == null) || DateUtils.after(
                    modificationDate, unModifiedSince));

            if (!isUnModifiedSince) {
                result = Status.CLIENT_ERROR_PRECONDITION_FAILED;
            }
        }

        return result;
    }

    /**
     * Returns the condition based on the modification date of the requested
     * variant.<br>
     * <br>
     * Note that when used with HTTP connectors, this property maps to the
     * "If-Unmodified-Since" header.
     * 
     * @return The condition date.
     */
    public Date getUnmodifiedSince() {
        return this.unmodifiedSince;
    }

    /**
     * Indicates if there are some conditions set.
     * 
     * @return True if there are some conditions set.
     */
    public boolean hasSome() {
        return (((this.match != null) && !this.match.isEmpty())
                || ((this.noneMatch != null) && !this.noneMatch.isEmpty())
                || (getModifiedSince() != null) || (getUnmodifiedSince() != null));
    }

    /**
     * Sets the modifiable list of tags that must be matched.<br>
     * <br>
     * Note that when used with HTTP connectors, this property maps to the
     * "If-Match" header.
     * 
     * @param tags
     *            The "if-match" condition.
     */
    public void setMatch(List<Tag> tags) {
        this.match = tags;
    }

    /**
     * Sets the condition based on the modification date of the requested
     * variant.<br>
     * <br>
     * Note that when used with HTTP connectors, this property maps to the
     * "If-Modified-Since" header.
     * 
     * @param date
     *            The modification date.
     */
    public void setModifiedSince(Date date) {
        this.modifiedSince = DateUtils.unmodifiable(date);
    }

    /**
     * Sets the modifiable list of tags that mustn't match. Creates a new
     * instance if no one has been set.<br>
     * <br>
     * Note that when used with HTTP connectors, this property maps to the
     * "If-None-Match" header.
     * 
     * @param tags
     *            The list of tags that mustn't match.
     */
    public void setNoneMatch(List<Tag> tags) {
        this.noneMatch = tags;
    }

    /**
     * Sets the condition based on the modification date of the requested
     * variant.<br>
     * <br>
     * Note that when used with HTTP connectors, this property maps to the
     * "If-Unmodified-Since" header.
     * 
     * @param date
     *            The condition date.
     */
    public void setUnmodifiedSince(Date date) {
        this.unmodifiedSince = DateUtils.unmodifiable(date);
    }

}