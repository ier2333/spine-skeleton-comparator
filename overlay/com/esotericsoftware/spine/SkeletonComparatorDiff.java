package com.esotericsoftware.spine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import com.badlogic.gdx.files.FileHandle;

class SkeletonComparatorDiff {
	static CompareResult compare (LoadedSkeletonInfo skeletonA, LoadedSkeletonInfo skeletonB) {
		List<String> animationsOnlyInA = difference(skeletonA.animationNames, skeletonB.animationNames);
		List<String> animationsOnlyInB = difference(skeletonB.animationNames, skeletonA.animationNames);
		List<String> bonesOnlyInA = difference(keys(skeletonA.boneParents), keys(skeletonB.boneParents));
		List<String> bonesOnlyInB = difference(keys(skeletonB.boneParents), keys(skeletonA.boneParents));
		List<BoneParentChange> bonesParentChanged = compareBoneParents(skeletonA.boneParents, skeletonB.boneParents);

		return new CompareResult(skeletonA, skeletonB, animationsOnlyInA, animationsOnlyInB, bonesOnlyInA, bonesOnlyInB,
			bonesParentChanged);
	}

	private static List<String> difference (List<String> source, List<String> other) {
		TreeSet<String> result = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
		result.addAll(source);
		for (String value : other)
			result.remove(value);
		return new ArrayList<String>(result);
	}

	private static List<String> keys (Map<String, String> values) {
		return new ArrayList<String>(values.keySet());
	}

	private static List<BoneParentChange> compareBoneParents (Map<String, String> boneParentsA, Map<String, String> boneParentsB) {
		TreeSet<String> sharedBoneNames = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
		sharedBoneNames.addAll(boneParentsA.keySet());
		sharedBoneNames.retainAll(boneParentsB.keySet());

		List<BoneParentChange> changes = new ArrayList<BoneParentChange>();
		for (String boneName : sharedBoneNames) {
			String parentA = boneParentsA.get(boneName);
			String parentB = boneParentsB.get(boneName);
			if (!same(parentA, parentB)) changes.add(new BoneParentChange(boneName, parentA, parentB));
		}
		return changes;
	}

	private static boolean same (String valueA, String valueB) {
		return valueA == null ? valueB == null : valueA.equals(valueB);
	}

	static class LoadedSkeletonInfo {
		final FileHandle file;
		final SkeletonData skeletonData;
		final List<String> animationNames;
		final Map<String, String> boneParents;

		LoadedSkeletonInfo (FileHandle file, SkeletonData skeletonData, List<String> animationNames,
			Map<String, String> boneParents) {
			this.file = file;
			this.skeletonData = skeletonData;
			this.animationNames = Collections.unmodifiableList(animationNames);
			this.boneParents = Collections.unmodifiableMap(new LinkedHashMap<String, String>(boneParents));
		}
	}

	static class BoneParentChange {
		final String boneName;
		final String parentA;
		final String parentB;

		BoneParentChange (String boneName, String parentA, String parentB) {
			this.boneName = boneName;
			this.parentA = parentA;
			this.parentB = parentB;
		}
	}

	static class CompareResult {
		final LoadedSkeletonInfo skeletonA;
		final LoadedSkeletonInfo skeletonB;
		final List<String> animationsOnlyInA;
		final List<String> animationsOnlyInB;
		final List<String> bonesOnlyInA;
		final List<String> bonesOnlyInB;
		final List<BoneParentChange> bonesParentChanged;

		CompareResult (LoadedSkeletonInfo skeletonA, LoadedSkeletonInfo skeletonB, List<String> animationsOnlyInA,
			List<String> animationsOnlyInB, List<String> bonesOnlyInA, List<String> bonesOnlyInB,
			List<BoneParentChange> bonesParentChanged) {
			this.skeletonA = skeletonA;
			this.skeletonB = skeletonB;
			this.animationsOnlyInA = Collections.unmodifiableList(animationsOnlyInA);
			this.animationsOnlyInB = Collections.unmodifiableList(animationsOnlyInB);
			this.bonesOnlyInA = Collections.unmodifiableList(bonesOnlyInA);
			this.bonesOnlyInB = Collections.unmodifiableList(bonesOnlyInB);
			this.bonesParentChanged = Collections.unmodifiableList(bonesParentChanged);
		}

		String summaryText () {
			StringBuilder text = new StringBuilder(128);
			text.append("Animations: A=");
			text.append(skeletonA.animationNames.size());
			text.append(", B=");
			text.append(skeletonB.animationNames.size());
			text.append(", A only=");
			text.append(animationsOnlyInA.size());
			text.append(", B only=");
			text.append(animationsOnlyInB.size());
			text.append('\n');
			text.append("Bones: A=");
			text.append(skeletonA.boneParents.size());
			text.append(", B=");
			text.append(skeletonB.boneParents.size());
			text.append(", A only=");
			text.append(bonesOnlyInA.size());
			text.append(", B only=");
			text.append(bonesOnlyInB.size());
			text.append(", parent changed=");
			text.append(bonesParentChanged.size());
			return text.toString();
		}
	}
}
