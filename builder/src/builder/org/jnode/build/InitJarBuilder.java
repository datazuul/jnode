/*
 * $Id$
 */
package org.jnode.build;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.GZip;
import org.apache.tools.ant.taskdefs.Jar;
import org.apache.tools.ant.taskdefs.Manifest;
import org.apache.tools.ant.types.FileSet;
import org.jnode.plugin.PluginDescriptor;
import org.jnode.plugin.PluginException;
import org.jnode.plugin.PluginPrerequisite;
import org.jnode.plugin.model.PluginJar;

/**
 * @author Ewout Prangsma (epr@users.sourceforge.net)
 */
public class InitJarBuilder extends AbstractPluginsTask {

	private File destFile;

	public void execute() throws BuildException {

		final long start = System.currentTimeMillis();

		final long lmDest = destFile.lastModified();
		final long lmPIL = getPluginListFile().lastModified();

		final PluginList piList;
		final long lmPI;
		try {
			piList = getPluginList();
			lmPI = piList.lastModified();
		} catch (PluginException ex) {
			throw new BuildException(ex);
		} catch (IOException ex) {
			throw new BuildException(ex);
		}

		if ((lmPIL < lmDest) && (lmPI < lmDest)) {
			// No need to do anything, skip
			return;
		}
		destFile.delete();
		final File tmpFile = new File(destFile + ".tmp");
		tmpFile.delete();

		try {
			// Load the plugin descriptors
			/*
			 * final PluginRegistry piRegistry; piRegistry = new PluginRegistryModel(piList.getDescriptorUrlList());
			 */

			final Jar jarTask = new Jar();
			jarTask.setProject(getProject());
			jarTask.setTaskName(getTaskName());
			jarTask.setDestFile(tmpFile);
			jarTask.setCompress(false);

			final Manifest mf = piList.getManifest();
			if (mf != null) {
				jarTask.addConfiguredManifest(mf);
			}
			
			final URL[] pluginList = piList.getPluginList();
			final ArrayList pluginJars = new ArrayList(pluginList.length);
			for (int i = 0; i < pluginList.length; i++) {
				final URL url = pluginList[i];
				final BuildPluginJar piJar = new BuildPluginJar(url);
				if (piJar.getDescriptor().isSystemPlugin()) {
					log("System plugin " + piJar.getDescriptor().getId() +" in plugin-list will be ignored", Project.MSG_WARN);
				} else {
					pluginJars.add(piJar);				    
				}
			}
			testPluginPrerequisites(pluginJars);
			final List sortedPluginJars = sortPlugins(pluginJars);
			
			for (Iterator i = sortedPluginJars.iterator(); i.hasNext(); ) {
				final BuildPluginJar piJar = (BuildPluginJar)i.next();
				pluginJars.add(piJar);
				final File f = new File(piJar.getPluginUrl().getPath());
				final FileSet fs = new FileSet();
				fs.setDir(f.getParentFile());
				fs.setIncludes(f.getName());
				jarTask.addFileset(fs);
			}
			
			/*
			 * for (Iterator i = piRegistry.getDescriptorIterator(); i.hasNext(); ) { final PluginDescriptor descr = (PluginDescriptor)i.next(); final Runtime rt = descr.getRuntime(); if (rt != null) {
			 * final Library[] libs = rt.getLibraries(); for (int l = 0; l < libs.length; l++) { processLibrary(jarTask, libs[l], fileSets, getPluginDir()); } }
			 */

			// Now create the jar file
			jarTask.execute();

			// Now zip it
			final GZip gzipTask = new GZip();
			gzipTask.setProject(getProject());
			gzipTask.setTaskName(getTaskName());
			gzipTask.setSrc(tmpFile);
			gzipTask.setZipfile(destFile);
			gzipTask.execute();
			tmpFile.delete();

		} catch (Exception ex) {
			ex.printStackTrace();
			throw new BuildException(ex);
		}
		final long end = System.currentTimeMillis();
		log("Building initjar took " + (end - start) + "ms");
	}

	/**
	 * @param file
	 */
	public void setDestFile(File file) {
		destFile = file;
	}

	/**
	 * Ensure that all plugin prerequisites are met.
	 * @throws BuildException
	 */
	protected void testPluginPrerequisites(List pluginJars) 
	throws BuildException {
	    final HashSet ids = new HashSet();
		
		for (Iterator i = pluginJars.iterator(); i.hasNext(); ) {
		    final PluginJar piJar = (PluginJar)i.next();
			final PluginDescriptor descr = piJar.getDescriptor();
			ids.add(descr.getId());
		}
		for (Iterator i = pluginJars.iterator(); i.hasNext(); ) {
		    final PluginJar piJar = (PluginJar)i.next();
			final PluginDescriptor descr = piJar.getDescriptor();
			final PluginPrerequisite[] prereqs = descr.getPrerequisites();
			for (int j = 0; j < prereqs.length; j++) {
			    if (!ids.contains(prereqs[j].getPluginId())) {
					throw new BuildException("Cannot find plugin " + prereqs[j].getPluginId() + ", which is required by " + descr.getId());
				}
			}
		}
	}
	
	/**
	 * Sort the plugins based on dependencies.
	 * @param pluginJars
	 */
	protected List sortPlugins(List pluginJars) {
	    final ArrayList result = new ArrayList(pluginJars.size());
	    final HashSet ids = new HashSet();
	    while (!pluginJars.isEmpty()) {
	        for (Iterator i = pluginJars.iterator(); i.hasNext(); ) {
	            final BuildPluginJar piJar = (BuildPluginJar)i.next();
	            if (piJar.hasAllPrerequisitesInSet(ids)) {
	                log(piJar.getDescriptor().getId(), Project.MSG_VERBOSE);
	                result.add(piJar);
	                ids.add(piJar.getDescriptor().getId());
	                i.remove();
	            }
	        }
	    }
	    return result;
	}
	
	static class BuildPluginJar extends PluginJar {
	    
	    private final URL pluginUrl;
	    
	    /**
         * @param pluginUrl
         * @throws PluginException
         * @throws IOException
         */
        BuildPluginJar(URL pluginUrl)
                throws PluginException, IOException {
            super(null, pluginUrl);
            this.pluginUrl = pluginUrl;
        }
        /**
         * @return Returns the pluginUrl.
         */
        final URL getPluginUrl() {
            return this.pluginUrl;
        }
        
        public boolean hasAllPrerequisitesInSet(Set ids) {
			final PluginDescriptor descr = getDescriptor();
			final PluginPrerequisite[] prereqs = descr.getPrerequisites();
			for (int j = 0; j < prereqs.length; j++) {
			    if (!ids.contains(prereqs[j].getPluginId())) {
			        return false;
				}
			}
			return true;
        }
	}
}
