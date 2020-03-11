/* 
 * This file is part of the PDF Split And Merge source code
 * Created on 16/apr/2014
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
package org.pdfsam.merge;

import static org.apache.commons.lang3.StringUtils.trim;

import java.util.Objects;
import java.util.function.Consumer;

import org.pdfsam.i18n.DefaultI18nContext;
import org.pdfsam.support.params.TaskParametersBuildStep;
import org.pdfsam.ui.selection.multiple.FileColumn;
import org.pdfsam.ui.selection.multiple.IntColumn;
import org.pdfsam.ui.selection.multiple.LoadingColumn;
import org.pdfsam.ui.selection.multiple.LongColumn;
import org.pdfsam.ui.selection.multiple.MultipleSelectionPane;
import org.pdfsam.ui.selection.multiple.PageRangesColumn;
import org.pdfsam.ui.selection.multiple.SelectionTableColumn;
import org.sejda.conversion.exception.ConversionException;
import org.sejda.model.input.PdfMergeInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Start - Imports needed for change request ps#2
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.sejda.model.pdf.page.PageRange;
import org.pdfsam.ui.selection.multiple.SelectionTableRowData;
import org.sejda.model.input.PdfFileSource;
import javafx.collections.ObservableList;
// End - Imports needed for change request ps#2

/**
 * Selection panel for the merge module.
 * 
 * @author Andrea Vacondio
 *
 */
public class MergeSelectionPane extends MultipleSelectionPane
        implements TaskParametersBuildStep<MergeParametersBuilder> {
    private static final Logger LOG = LoggerFactory.getLogger(MergeSelectionPane.class);

    public MergeSelectionPane(String ownerModule) {
        super(ownerModule, true, true,
                new SelectionTableColumn<?>[] { new LoadingColumn(ownerModule), FileColumn.NAME, LongColumn.SIZE,
                        IntColumn.PAGES, LongColumn.LAST_MODIFIED, new PageRangesColumn(DefaultI18nContext.getInstance()
                                .i18n("Double click to set pages you want to merge (ex: 2 or 5-23 or 2,5-7,12-)")) });
    }

    @Override
    public void apply(MergeParametersBuilder builder, Consumer<String> onError) {
        try {
        	// For each entry in the PDF file list, a 'Page range' can optionally be provided to indicate which
        	// pages to include in the merged document.  The original implementation converted the page ranges 
        	// into a set of PageRange objects and associated them with the specific file.  To support intersecting
        	// ranges, the following logic adds a separate entry for each sourc_file & PageRange combination.
        	
        	// Storage for the table items 
        	ObservableList<SelectionTableRowData> tableItems = table().getItems();
        	
        	// Storage for PdfMergeInputs
        	List<PdfMergeInput> pdfMergeInputs = new ArrayList<>();
        	
            // Iterate over each item in the table 
            for (SelectionTableRowData item : tableItems) {
                // Remove leading and trailing spaces, then process if anything is left
                if(!Objects.equals("0", trim(item.pageSelection.get()))) {
                    // Create a PdfFileSource that contains the filename/path for this 'item' 
            		PdfFileSource source = item.descriptor().toPdfFileSource();
                	
            		// Convert the range value(s to a set of PageRange objects
                	Set<PageRange> pageRangeSet = item.toPageRangeSet();
            		
                	// Process all PageRange objects in the set 
                	for (PageRange pageRange : pageRangeSet) {
                		// Need to create a PageRange set because that's what the PdfMergeInput constructor requires
                		Set<PageRange> pageSelection = new HashSet<>();
                                                
                        // Add this pageRange to the set 
                        pageSelection.add(pageRange);
                                               
                        // Create a new PdfMergeInput object and add to the list of PdfMergeInputs
                        pdfMergeInputs.add(new PdfMergeInput(source, pageSelection));
                    }
                }
            }

            // For each PdfMergeInput in the list, add it to the builder
            pdfMergeInputs.stream().forEach(builder::addInput);

            if (!builder.hasInput()) {
                onError.accept(DefaultI18nContext.getInstance().i18n("No PDF document has been selected"));
            }
        } catch (ConversionException e) {
            LOG.error(e.getMessage());
            onError.accept(e.getMessage());
        }
    }
}
