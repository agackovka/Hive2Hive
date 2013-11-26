package org.hive2hive.core.process.upload.newfile;

import java.io.File;
import java.io.IOException;
import java.security.KeyPair;
import java.security.PublicKey;

import org.hive2hive.core.log.H2HLogger;
import org.hive2hive.core.log.H2HLoggerFactory;
import org.hive2hive.core.model.FileTreeNode;
import org.hive2hive.core.model.UserProfile;
import org.hive2hive.core.network.data.UserProfileManager;
import org.hive2hive.core.process.ProcessStep;
import org.hive2hive.core.process.upload.UploadFileProcessContext;
import org.hive2hive.core.security.EncryptionUtil;

/**
 * A step adding the new file (node) into the user profile (tree)
 * 
 * @author Nico
 * 
 */
public class UpdateUserProfileStep extends ProcessStep {

	private static final H2HLogger logger = H2HLoggerFactory.getLogger(UpdateUserProfileStep.class);

	private PublicKey parentKey;

	@Override
	public void start() {
		NewFileProcessContext context = (NewFileProcessContext) getProcess().getContext();
		logger.debug("Start updating the user profile where adding the file: " + context.getFile().getName());

		// set the user profile from the previous step
		UserProfileManager profileManager = context.getProfileManager();

		try {
			UserProfile userProfile = profileManager.getUserProfile(getProcess());
			profileManager.startModification(getProcess());

			// create a file tree node in the user profile
			addFileToUserProfile(userProfile, context.getFile(), context.getNewMetaKeyPair());

			profileManager.putUserProfile(getProcess());

			// TODO next steps:
			// 1. notify other clients as the next step
			// 2. check if too many versions of that file exist --> remove old versions if necessary
			getProcess().setNextStep(null);
		} catch (Exception e) {
			getProcess().stop(e.getMessage());
		}
	}

	/**
	 * Generates a {@link FileTreeNode} that can be added to the DHT
	 * 
	 * @param userProfile
	 * 
	 * @param file the file to be added
	 * @param fileRoot the root file of this H2HNode instance
	 * @param rootNode the root node in the tree
	 * @throws IOException
	 */
	private void addFileToUserProfile(UserProfile userProfile, File file, KeyPair fileKeys)
			throws IOException {
		UploadFileProcessContext context = (UploadFileProcessContext) getProcess().getContext();
		File fileRoot = context.getFileManager().getRoot();

		// new file
		// the parent of the new file should already exist in the tree
		File parent = file.getParentFile();

		// find the parent node using the relative path to navigate there
		String relativeParentPath = parent.getAbsolutePath().replaceFirst(fileRoot.getAbsolutePath(), "");
		FileTreeNode parentNode = userProfile.getFileByPath(relativeParentPath);
		parentKey = parentNode.getKeyPair().getPublic();

		// use the file keys generated in a previous step where the meta document is stored
		if (file.isDirectory()) {
			new FileTreeNode(parentNode, fileKeys, file.getName());
		} else {
			byte[] md5 = EncryptionUtil.generateMD5Hash(file);
			new FileTreeNode(parentNode, fileKeys, file.getName(), md5);
		}
	}

	@Override
	public void rollBack() {
		// remove the file from the user profile
		NewFileProcessContext context = (NewFileProcessContext) getProcess().getContext();
		UserProfileManager profileManager = context.getProfileManager();

		try {
			UserProfile userProfile = profileManager.getUserProfile(getProcess());
			profileManager.startModification(getProcess());

			FileTreeNode parentNode = userProfile.getFileById(parentKey);
			FileTreeNode childNode = parentNode.getChildByName(context.getFile().getName());
			parentNode.removeChild(childNode);
			profileManager.putUserProfile(getProcess());
		} catch (Exception e) {
			// ignore
		}

		getProcess().nextRollBackStep();
	}
}