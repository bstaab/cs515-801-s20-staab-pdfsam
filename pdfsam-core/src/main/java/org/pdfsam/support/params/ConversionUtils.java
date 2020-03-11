/* 
 * This file is part of the PDF Split And Merge source code
 * Created on 22 giu 2016
 * Copyright 2017 by Sober Lemur S.a.s. di Vacondio Andrea (info@pdfsam.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as 
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.pdfsam.support.params;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sejda.conversion.AdapterUtils.splitAndTrim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.stream.IntStream;

import org.pdfsam.i18n.DefaultI18nContext;
import org.sejda.commons.collection.NullSafeSet;
import org.sejda.conversion.exception.ConversionException;
import org.sejda.model.pdf.page.PageRange;

/**
 * @author Andrea Vacondio
 *
 */
public final class ConversionUtils {

    private ConversionUtils() {
        // hide
    }

    /**
     * @return the post-processed {@link PageRange} set for the given string, an empty set otherwise.
     * 		   NOTE: overlapping, duplicate and intersecting will be converted into a valid union 
     *               of all page descriptions
     */
    public static Set<PageRange> postProcessPageRangeSet(Set<PageRange> ranges) {
        if (ranges.isEmpty()) {
            return ranges;
        }

        // Storage for union of page numbers
        ArrayList<Integer> pageNumberList = new ArrayList<Integer>();
        
        // Storage for optimized page ranges
        Set<PageRange> pageRangeSet = new NullSafeSet<>();

        // Get a list of all pages with bound ranges (end != UNBOUNDED_END)
        // NOTE: This will automatically handle intersections by dropping duplicate entries
        for (PageRange range : ranges) {
        	// Skip unbound ranges
            if (!range.isUnbounded()) {
            	// Convert range to list
            	for (int pageNum : IntStream.rangeClosed(range.getStart(), range.getEnd()).toArray()) {
            		// If page number is not in union, add it
            		if (!pageNumberList.contains(pageNum)) {
            			pageNumberList.add(pageNum);
            		}
            	}
            }
        }
        
        // Sort the list in ascending order
        Collections.sort(pageNumberList);
        
        // Find the lowest starting page number for all unbounded ranges
        int unbounded_start = Integer.MAX_VALUE;
        int unbounded_end = Integer.MAX_VALUE;
        for (PageRange range : ranges) {
        	// Only process unbound ranges
            if (range.isUnbounded()) {
            	// Update variabales if this is the lowest value
            	if (range.getStart() < unbounded_start) {
            		unbounded_start = range.getStart(); 
            		unbounded_end  = range.getEnd();
            	}
            }
        }
        
        // Update the union of pages if an unbound range was found 
        if (unbounded_start < Integer.MAX_VALUE) {
	        int pos = -1;
	        // Get the index of thee first page number >= unbounded_start
	        // NOTE: Requires sorted list in ascending order
	        for (int pageNum : pageNumberList) {
	        	if (pageNum >= unbounded_start) {
	        		pos = pageNumberList.indexOf(pageNum);
	        		break;
	        	}
	        }
	        
	        // Remove all page numbers >= unbounded_start
	        if (pos >= 0) {
	        	int pageNumberListSize = pageNumberList.size();
	        	pageNumberList.subList(pos,pageNumberListSize).clear();
	        }
	        
	        // Add the unbounded range to the end of the page list
	        pageNumberList.add(unbounded_start);
	        pageNumberList.add(unbounded_end);
        }
        
        // Build new pageRanges
        boolean newRange = true;
        int start_page = 0;
        int last_page = 0;
        int end_page = 0;
        int pageNumberListSize = pageNumberList.size();
        
        PageRange range;
        for (int pageIdx = 0; pageIdx < pageNumberListSize; pageIdx++)
        {
        	// Get page number from list index
        	int currentPageNum = pageNumberList.get(pageIdx);
        	
        	// Start of a new range
        	if (newRange) {
        		newRange = false;
        		start_page = currentPageNum;
        		last_page = currentPageNum;
        		end_page = currentPageNum;

        		// Check to see if this is the last item in the list 
        		if (pageIdx == pageNumberListSize -1) {
            		range = new PageRange(start_page, end_page);
            		pageRangeSet.add(range);
        		}
        	}
        	// Handle unbounded_end value.
        	// This should be the last value in the list
        	else if (currentPageNum == unbounded_end) {
        		end_page = currentPageNum;
        		range = new PageRange(start_page);
        		pageRangeSet.add(range);
        	}
        	// Check for a gap in the page numbers
        	else if (currentPageNum != (last_page + 1)) {
        		end_page = last_page;
        		range = new PageRange(start_page, end_page);
        		pageRangeSet.add(range);
        		last_page = currentPageNum;
        		start_page = currentPageNum;
        	}
        	// Check for last item in the list
        	else if (pageIdx == pageNumberListSize -1) {
        		end_page = currentPageNum;
        		range = new PageRange(start_page, end_page);
        		pageRangeSet.add(range);
        		last_page = currentPageNum;
        		start_page = currentPageNum;
        	}
        	else {
        		last_page = currentPageNum;
        	}
        	
        }
        
        // Return new Page Range set
        return pageRangeSet;
    }
    
    
    /**
     * @return the {@link PageRange} set for the given string, an empty set otherwise.
     */
    public static Set<PageRange> toPageRangeSet(String selection) throws ConversionException {
        if (isNotBlank(selection)) {
            Set<PageRange> pageRangeSet = new NullSafeSet<>();
            String[] tokens = splitAndTrim(selection, ",");
            for (String current : tokens) {
                PageRange range = toPageRange(current);
                if (range.getEnd() < range.getStart()) {
                    throw new ConversionException(
                            DefaultI18nContext.getInstance().i18n("Invalid range: {0}.", range.toString()));
                }
                pageRangeSet.add(range);
            }
            // Post-process Page Range results befor returning
            //return postProcessPageRangeSet(pageRangeSet);
            // Return unprocessed RangeSet
            return pageRangeSet;
        }
        return Collections.emptySet();
    }

    private static PageRange toPageRange(String value) throws ConversionException {
        String[] limits = splitAndTrim(value, "-");
        if (limits.length > 2) {
            throw new ConversionException(DefaultI18nContext.getInstance().i18n(
                    "Ambiguous page range definition: {0}. Use following formats: [n] or [n1-n2] or [-n] or [n-]",
                    value));
        }
        if (limits.length == 1) {
            int limitNumber = parsePageNumber(limits[0]);
            if (value.endsWith("-")) {
                return new PageRange(limitNumber);
            }
            if (value.startsWith("-")) {
                return new PageRange(1, limitNumber);
            }
            return new PageRange(limitNumber, limitNumber);
        }
        return new PageRange(parsePageNumber(limits[0]), parsePageNumber(limits[1]));
    }

    private static int parsePageNumber(String value) throws ConversionException {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException nfe) {
            throw new ConversionException(DefaultI18nContext.getInstance().i18n("Invalid number: {0}.", value));
        }
    }
}
