/*
 * This code is licensed with an MIT License Copyright (c) 2016: ippcupttocs
 * and leverages components of (c) Copyright 2006 - 2016, Wowza Media Systems, LLC. All rights reserved.
 * Wowza is licensed pursuant to the Wowza Public License version 1.0, available at www.wowza.com/legal.
 * of which I have no idea how to call out appropriately in here.
 * This and PushPublishALL.java were my first foray into application development
 * and the first attempt at writting something in Java with zero training...
 * enjoy!
 @author Scott
 */
package org.mycompany.wowza.module;

import java.io.File;
import java.io.FileOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPath;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.wowza.util.HTTPUtils;
import com.wowza.wms.amf.AMFPacket;
import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.application.WMSProperties;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.module.ModuleBase;
import com.wowza.wms.stream.IMediaStream;
import com.wowza.wms.stream.IMediaStreamActionNotify2;
//AutoGenerateSMIL completes the process, mirroring the origin server with a matching manifest smil file AND/OR removing a stream from the Manifest.  
//Be sure to check the time-stamps in the client player API. If they match, you need to reload the manifest.

public class AutoGenerateSMIL extends ModuleBase {
	private String originAppName = null;
	private int originAdminPort = 8086;
	private String lastStreamPublished = null;
	private String streamList = null;
	private String streamSource = null;
	private String smilFileNameSuffix = null;
	private String storageDir = null;

	class StreamNotify implements IMediaStreamActionNotify2 {
		//
		public void onPlay(IMediaStream stream, String streamName,
				double playStart, double playLen, int playReset) {
		}

		//
		public void onPause(IMediaStream stream, boolean isPause,
				double location) {
		}

		//
		public void onSeek(IMediaStream stream, double location) {
		}

		//
		public void onStop(IMediaStream stream) {
		}

		//
		public void onMetaData(IMediaStream stream, AMFPacket metaDataPacket) {
		}

		//
		public void onPauseRaw(IMediaStream stream, boolean isPause,
				double location) {
		}

		// onPublish
		public void onPublish(IMediaStream stream, String streamName,
				boolean isRecord, boolean isAppend) {
			WMSLogger log = WMSLoggerFactory.getLogger(null);
			removeOldSmil(log);
			boolean unPublished = false;
			// Pattern match final incoming stream name
			Pattern fullStreamPattern = Pattern.compile(streamSource + "("
					+ lastStreamPublished + ")");
			Matcher streamMatch = fullStreamPattern.matcher(streamName);

			if (streamMatch.find()) {
				log.info("AutoGenerateSMIL.onPublish() - incoming stream: "
						+ streamName + " matches: "
						+ fullStreamPattern.toString());
				streamMatcher(stream, streamName, unPublished, log);
			} else {
				log.debug("AutoGenerateSMIL.onPublish(): " + streamName
						+ " does not match: " + fullStreamPattern.toString());
			}
		}

		// onUnPublished
		public void onUnPublish(IMediaStream stream, String streamName,
				boolean isRecord, boolean isAppend) {
			WMSLogger log = WMSLoggerFactory.getLogger(null);
			boolean unPublished = true;
			// Pattern match all expected stream names
			Pattern fullStreamPattern = Pattern.compile(streamSource + "("
					+ streamList + ")");
			Matcher streamMatch = fullStreamPattern.matcher(streamName);

			if (streamMatch.find()) {
				log.info("AutoGenerateSMIL.onUnPublish() - incoming stream: "
						+ streamName + " matches: "
						+ fullStreamPattern.toString());
				streamMatcher(stream, streamName, unPublished, log);
			} else {
				log.debug("AutoGenerateSMIL.onUnPublish(): " + streamName
						+ " does not match: " + fullStreamPattern.toString());
			}
		}
	}

	public void onAppStart(IApplicationInstance appInstance) {
		WMSLogger log = WMSLoggerFactory.getLogger(null);
		try {
			WMSProperties props = appInstance.getProperties();
			if (props != null) {
				this.originAppName = props.getPropertyStr("orignAppName",
						"liveorigin"); // Origin server application name
				this.originAdminPort = props.getPropertyInt("originAdminPort",
						8086); // Origin server administration port
				this.lastStreamPublished = props.getPropertyStr(
						"lastStreamPushed", ""); // Single origin transcode
													// enabled StreamNames.
													// Example: _144p
				this.streamList = props.getPropertyStr("allStreamsPushed", "");
				this.streamSource = props.getPropertyStr(
						"pushedStreamSourceRegex", ""); // Regex pattern
														// matching
														// ${SourceStreamName}
														// published to Origin
														// server
				this.smilFileNameSuffix = props.getPropertyStr(
						"smilFilenameSuffix", "_all"); // Suffix of smil file
														// stored in content
														// directory of Edge
														// server
				this.storageDir = appInstance.getStreamStoragePath(); // Get
																		// StorageDir
																		// from
																		// Application.xml
																		// example
																		// ${com.wowza.wms.context.VHostConfigHome}/content

				log.info("AutoGenerateSMIL.onAppStart(): "
						+ storageDir.toString() + " medialist app: "
						+ originAppName + " admin port: " + originAdminPort
						+ " StreamName list: " + lastStreamPublished
						+ " input stream regex: " + streamSource
						+ "output file suffix: " + smilFileNameSuffix);
			} else {
				log.error("AutoGenerateSMIL.onAppStart(): Application.xml properties are null for module: PublishOneToMany");
			}

		} catch (Exception e) {
			log.error("AutoGenerateSMIL.onAppStart(): " + e.toString()
					+ "Check Application.xml for default Properties");
		}
	}

	public void onStreamCreate(IMediaStream stream) {
		stream.addClientListener(new StreamNotify());
	}

	public void onStreamDestory(IMediaStream stream, String streamName) {

	}

	private void streamMatcher(IMediaStream stream, String streamName,
			boolean unPublished, WMSLogger log) {

		log.debug("AutoGenerateSMIL.streamMatcher() - streamName: "
				+ streamName);
		try {
			String streamBaseName = streamName.split("_")[0];
			String smilFileName = streamBaseName + smilFileNameSuffix + ".smil";
			File smilFullPath = new File(storageDir + "/" + smilFileName);
			if (smilFullPath.exists()) {
				log.info("AutoGenerateSMIL.streamMatcher() - smilFullPath: "
						+ smilFullPath.toString()
						+ " already exists, overwriting");
			} else {
				log.info("AutoGenerateSMIL.streamMatcher() - smilFullPath: "
						+ smilFullPath.toString() + " needs to be written");
			}
			smilHttpGet(smilFullPath, smilFileName, streamBaseName, stream,
					streamName, unPublished, log);
		} catch (Exception e) {
			log.error("AutoGenerateSMIL.streamMatcher(): "
					+ "Threw an exception: " + e);
		}
	}

	private synchronized void smilHttpGet(File smilFullPath,
			String smilFileName, String streamBaseName, IMediaStream stream,
			String streamName, boolean unPublished, WMSLogger log) {

		String medialistUrl = "http://" + stream.getClient().getIp().toString()
				+ ":" + originAdminPort + "/medialist?streamname=ngrp:"
				+ smilFileName.replace(".smil", "") + "&application="
				+ originAppName + "&format=smil";
		try {
			if ((smilFullPath.exists()) && (unPublished)) {
				File smilFile = new File(smilFullPath.toString());
				DocumentBuilderFactory docFactory = DocumentBuilderFactory
						.newInstance();
				DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
				Document smilIn = docBuilder.parse(smilFile);
				XPathFactory xpf = XPathFactory.newInstance();
				XPath xpath = xpf.newXPath();
				XPathExpression expression = xpath.compile("//video[@src='mp4:"
						+ streamName + "']");
				Object result = expression.evaluate(smilIn,
						XPathConstants.NODESET);
				NodeList nodes = (NodeList) result;
				for (int i = 0; i < nodes.getLength(); i++) {
					Node node = nodes.item(i);
					node.getParentNode().removeChild(node);
				}
				// get rid of whitespace lines
				smilIn.getDocumentElement().normalize();
				XPathExpression xpath1 = XPathFactory.newInstance().newXPath()
						.compile("//text()[normalize-space(.) = '']");
				NodeList blankTextNodes = (NodeList) xpath1.evaluate(smilIn,
						XPathConstants.NODESET);
				for (int i = 0; i < blankTextNodes.getLength(); i++) {
					blankTextNodes.item(i).getParentNode()
							.removeChild(blankTextNodes.item(i));
				}
				Transformer tr = TransformerFactory.newInstance()
						.newTransformer();
				tr.setOutputProperty(OutputKeys.INDENT, "yes");
				tr.setOutputProperty(OutputKeys.METHOD, "xml");
				tr.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
				DOMSource output = new DOMSource(smilIn);
				StreamResult sr = new StreamResult(new FileOutputStream(
						smilFullPath, false));
				tr.transform(output, sr);
			} else {
				httpRetryOnException retry = new httpRetryOnException();
				while (retry.shouldRetry()) {
					try {
						boolean success = HTTPUtils.HTTPRequestToFile(
								smilFullPath, medialistUrl, "GET", null, null);
						if (success) {
							log.info("AutoGenerateSMIL.smilHttpGet(): "
									+ "GET medialist 200: " + medialistUrl);
							break;
						} else {
							log.info("AutoGenerateSMIL.smilHttpGet(): "
									+ "GET medialist: retry " + medialistUrl);
							retry.errorOccured();
						}
					} catch (Exception e) {
						log.error("AutoGenerateSMIL.smilHttpGet(): "
								+ " threw an exception: " + e + " url: "
								+ medialistUrl + " path: "
								+ smilFullPath.toString());
					}
				}
				// Cleanup all param nodes that exist in medialist, but cause
				// duplicate
				// values to be displayed if retained in the edge server's SMIL
				File smilFile = new File(smilFullPath.toString());
				DocumentBuilderFactory docFactory = DocumentBuilderFactory
						.newInstance();
				DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
				Document smilIn = docBuilder.parse(smilFile);
				XPathFactory xpf = XPathFactory.newInstance();
				XPath xpath = xpf.newXPath();
				// remove added param from medialist that tend to appear as
				// duplicates if added to the smil
				XPathExpression expression = xpath.compile("//param[@*]");
				Object result = expression.evaluate(smilIn,
						XPathConstants.NODESET);
				NodeList nodes = (NodeList) result;
				for (int i = 0; i < nodes.getLength(); i++) {
					Node node = nodes.item(i);
					node.getParentNode().removeChild(node);
				}
				// get rid of whitespace lines
				smilIn.getDocumentElement().normalize();
				XPathExpression xpath1 = XPathFactory.newInstance().newXPath()
						.compile("//text()[normalize-space(.) = '']");
				NodeList blankTextNodes = (NodeList) xpath1.evaluate(smilIn,
						XPathConstants.NODESET);
				for (int i = 0; i < blankTextNodes.getLength(); i++) {
					blankTextNodes.item(i).getParentNode()
							.removeChild(blankTextNodes.item(i));
				}
				Transformer tr = TransformerFactory.newInstance()
						.newTransformer();
				tr.setOutputProperty(OutputKeys.INDENT, "yes");
				tr.setOutputProperty(OutputKeys.METHOD, "xml");
				tr.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
				DOMSource output = new DOMSource(smilIn);
				StreamResult sr = new StreamResult(new FileOutputStream(
						smilFullPath, false));
				tr.transform(output, sr);
			}

		} catch (Exception e) {
			log.error("AutoGenerateSMIL.smilHttpGet(): Exception" + e
					+ " unPublished:" + unPublished);
		}
	}

	private void removeOldSmil(WMSLogger log) {
		// remove all smil files that are older than 30 days
		long daysBack = 30;
		final File directory = new File(storageDir);
		try {
			if (directory.exists()) {
				final File[] listFiles = directory.listFiles();
				long purgeTime = System.currentTimeMillis()
						- (daysBack * 24L * 60L * 60L * 1000L);
				for (File listFile : listFiles) {
					if ((listFile.toString().endsWith(".smil"))
							&& (listFile.lastModified() < purgeTime)) {
						listFile.delete();
						log.info("AutoGenerateSMIL.onStreamDestory().removeOldSmil() - File: "
								+ listFile.toString()
								+ " older than 30days - removed"
								+ listFile.toString());
					}
				}
			} else {
				log.error("AutoGenerateSMIL.onStreamDestory().removeOldSmil(): "
						+ directory.toString() + " does not exist");
			}
		} catch (Exception e) {
			log.error("AutoGenerateSMIL.onStreamDestory().removeOldSmil(): "
					+ "Threw an exception: " + e);
		}
	}

	private class httpRetryOnException {
		public static final int DEFAULT_RETRIES = 15;
		public static final long DEFAULT_WAIT_TIME_IN_MILLI = 5000; // 5 seconds
		private int numberOfRetries = 3;
		private int numberOfTriesLeft;
		private long timeToWait = 60000; // 60000=1 minute //300000=5 minutes

		public httpRetryOnException() {
			this(DEFAULT_RETRIES, DEFAULT_WAIT_TIME_IN_MILLI);
		}

		public httpRetryOnException(int numberOfRetries, long timeToWait) {
			this.numberOfRetries = numberOfRetries;
			numberOfTriesLeft = numberOfRetries;
			this.timeToWait = timeToWait;
		}

		/**
		 * @return true if there are tries left
		 */
		public boolean shouldRetry() {
			return numberOfTriesLeft > 0;
		}

		public void errorOccured() throws Exception {
			numberOfTriesLeft--;
			if (!shouldRetry()) {
				throw new Exception("Retry Failed: Total " + numberOfRetries
						+ " attempts made at interval " + getTimeToWait()
						+ "ms");
			}
			waitUntilNextTry();
		}

		public long getTimeToWait() {
			return timeToWait;
		}

		private void waitUntilNextTry() {
			try {
				Thread.sleep(getTimeToWait());
			} catch (InterruptedException ignored) {
			}
		}
	}

}
// Done
