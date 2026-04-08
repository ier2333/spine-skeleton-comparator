package com.esotericsoftware.spine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.files.FileHandle;
import com.esotericsoftware.spine.SkeletonComparatorDiff.LoadedSkeletonInfo;

class SkeletonComparatorLoader {
	static LoadedSkeletonInfo load (FileHandle skeletonFile) throws Exception {
		if (skeletonFile == null) throw new IllegalArgumentException("skeletonFile cannot be null.");

		FileHandle canonicalFile;
		try {
			canonicalFile = new FileHandle(skeletonFile.file().getCanonicalFile());
		} catch (Throwable ex) {
			canonicalFile = new FileHandle(skeletonFile.file().getAbsoluteFile());
		}

		SkeletonViewerAtlas atlas = new SkeletonViewerAtlas(canonicalFile);
		try {
			String extension = canonicalFile.extension();
			SkeletonLoader loader;
			if (extension.equalsIgnoreCase("json") || extension.equalsIgnoreCase("txt"))
				loader = new SkeletonJson(atlas);
			else
				loader = new SkeletonBinary(atlas);

			SkeletonData skeletonData = loader.readSkeletonData(canonicalFile);
			if (skeletonData.getBones().size == 0) throw new Exception("No bones in skeleton data.");

			return new LoadedSkeletonInfo(canonicalFile, skeletonData, collectAnimationNames(skeletonData),
				collectBoneParents(skeletonData));
		} finally {
			atlas.dispose();
		}
	}

	private static List<String> collectAnimationNames (SkeletonData skeletonData) {
		List<String> names = new ArrayList<String>(skeletonData.getAnimations().size);
		for (Animation animation : skeletonData.getAnimations())
			names.add(animation.getName());
		Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
		return names;
	}

	private static Map<String, String> collectBoneParents (SkeletonData skeletonData) {
		List<String> boneNames = new ArrayList<String>(skeletonData.getBones().size);
		for (BoneData bone : skeletonData.getBones())
			boneNames.add(bone.getName());
		Collections.sort(boneNames, String.CASE_INSENSITIVE_ORDER);

		Map<String, String> parentsByBone = new LinkedHashMap<String, String>(boneNames.size());
		for (String boneName : boneNames) {
			BoneData bone = skeletonData.findBone(boneName);
			BoneData parent = bone == null ? null : bone.getParent();
			parentsByBone.put(boneName, parent == null ? null : parent.getName());
		}
		return parentsByBone;
	}
}
