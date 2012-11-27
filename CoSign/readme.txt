Arx CoSign Integration Component

TODO:
Develop preferences

This component integrates WCC's content management and business processing with CoSign's digital
signature functionality.  This component modifies context menus, adding CoSign integration point
throughout the systems content interaction menus.

Requirements:
	Provide top menu addition for user(s) with Role equal CoSignAdmin.
	Top menu addition will allow for creation and editting of CoSign Signature Profiles.
	Provide content menu additions for user(s) with Role equal to CoSignSigner on content with CoSign
			meta values established (not on Workflow pages).
	Context menus will be located on the Search page, Doc Info page, Workflow Queue page, Workflow
			Review page, and Folders_g Folder contents display page.
	Context menus will allow for signing of content within Cosign server (not on Workflow pages) and
			review of past signature details.
	Provide search view template, adding columns for last signer, last sign time, signature count, and
			last signature status.
	Calls to CoSign server (Pull or Verify) will be logged for audit purposes.
	Verification meta will be "cached" in a WCC managed table after every pull request and, in cases
			where content was signed outside of WCC integration, on verify requests.
	Reviewing past signatures will first check local DB then CoSign, is nothing returned.
	Approving CoSign tagged content will load Workflow Review page, with greyed out context menu and
			CoSign Web Signing Ceremony in center frame.
	Navigating to Workflow Review page will load as standard

Installation requirements:
	DB Tables:
		CoSignHistory
			//////////////////////////////////////////////////
			/ Name            | Type      | Size | Null | PK /
			/================================================/
			/ ID              | NUMBER    |      |  F   | T  /
			/ PERFORMINGUSER  | VARCHAR2  | 50   |  T   | F  /
			/ DATEPERFORMED   | DATE      |      |  T   | F  /
			/ OPERATION       | VARCHAR2  | 50   |  T   | F  /
			/ ERRORMESSAGE    | VARCHAR2  | 250  |  T   | F  /
			/ DDOCNAME        | VARCHAR2  | 80   |  T   | F  /
			/ DID             | VARCHAR2  | 80   |  T   | F  /
			//////////////////////////////////////////////////

		CoSignSignatureDetails
			//////////////////////////////////////////////////
			/ Name            | Type      | Size | Null | PK /
			/================================================/
			/ SID             | NUMBER    |      |  F   | T  /
			/ DID             | VARCHAR2  | 80   |  F   | T  /
			/ DDOCNAME        | VARCHAR2  | 80   |  T   | F  /
			/ FIELDNAME       | VARCHAR2  | 80   |  T   | F  /
			/ STATUS          | VARCHAR2  | 80   |  T   | F  /
			/ SIGNINGTIME     | TIMESTAMP |      |  T   | F  /
			/ SIGNEREMAIL     | VARCHAR2  | 80   |  T   | F  /
			/ SIGNERNAME      | VARCHAR2  | 80   |  T   | F  /
			/ SIGNREASON      | VARCHAR2  | 200  |  T   | F  /
			/ CERTERRORSTATUS | VARCHAR2  | 80   |  T   | F  /
			/ X               | NUMBER    |      |  T   | F  /
			/ Y               | NUMBER    |      |  T   | F  /
			/ WIDTH           | NUMBER    |      |  T   | F  /
			/ HEIGHT          | NUMBER    |      |  T   | F  /
			/ PAGENUMBER      | NUMBER    |      |  T   | F  /
			/ DATEFORMAT      | VARCHAR2  | 40   |  T   | F  /
			/ TIMEFORMAT      | VARCHAR2  | 40   |  T   | F  /
			/ GRAPHICALIMAGE  | VARCHAR2  | 80   |  T   | F  /
			/ SIGNER          | VARCHAR2  | 80   |  T   | F  /
			/ SIGNDATE        | VARCHAR2  | 80   |  T   | F  /
			/ INITIALS        | VARCHAR2  | 10   |  T   | F  /
			/ LOGO            | VARCHAR2  | 80   |  T   | F  /
			/ SHOWTITLE       | VARCHAR2  | 10   |  T   | F  /
			/ SHOWREASON      | VARCHAR2  | 10   |  T   | F  /
			/ TITLE           | VARCHAR2  | 80   |  T   | F  /
			/ REASON          | VARCHAR2  | 200  |  T   | F  /
			//////////////////////////////////////////////////

	Content Type(s):
		CoSignSignatureProfile

	Meta Data Field(s):
			///////////////////////////////////////////////////////////
			/ Name                     | Type    | Index | Option     /
			/=========================================================/
			/ SignatureStatus          | Text    |   T   | Valid      /
			/                          |         |       | Invalid    /
			/                          |         |       | Not Signed /
			/ Signer                   | Text    |   T   |            /
			/ SignTime                 | Date    |   T   |            /
			/ SignatureCount           | Integer |   T   |            /
			/ CoSignRequiredSignatures | Text    |   T   | UD         /
			/ CoSignSignatureTag       | Text    |   T   |            /
			/ CoSignSignatureReasons   | Text    |   F   | UD         /
			///////////////////////////////////////////////////////////
			UD = User Defined

	Profile(s): None

	Rule(s):
		CoSign_RO (Global)
			/////////////////////////////////
			/ Name                   | Type /
			/================================
			/ xSignatureStatus       |  I   /
			/ xSigner                |  I   /
			/ xSignTime              |  I   /
			/ xSignatureCount        |  I   /
			/////////////////////////////////

		CoSign_EX (Global)
			/////////////////////////////////
			/ Name                   | Type /
			/===============================/
			/ CoSignSignatureReasons |  EX  /
			/////////////////////////////////

	Security:
		Security Group(s): CoSign (optional)(UD)
		Account(s): None
		Role(s):
			/////////////////////////////////
			/ Name         | Permission(s)  /
			/================================
			/ CoSignAdmin  | R/W CoSign     /
			/ CoSignSigner | R/W any        /
			/              | security group /
			/              | used for       /
			/              | signed content /
			/////////////////////////////////
			UD = User Defined

	View(s): (optional)
		CoSignRequiredSignaturesList - List of Role(s) required to act on any one content item to be signed.
				As well as the Role that is allowed to utilize one profile item.
		CoSignSignatureReasonsList - List of default Reason(s) to pre-load creation form with.

	Configuration(s):
		CoSign Server connection configurations
			wscServerAddress - URL server name to Web Signing Ceremony server
					(i.e. http://ec2-50-17-123-132.compute-1.amazonaws.com)
			wscServerPort - URL server port to Web Signing Ceremony server (i.e. 80)
			CoSignServerPath - URL web root to Web Signing Ceremony server (i.e. \wsc\)

		Signature RequestGlobal configurations
			CoSign.Url.finishURL - URL pointing to COSIGN_CHECKIN_SIGNEDDOCUMENT service on WCC
					(i.e. http://ec2-50-17-123-132.compute-1.amazonaws.com:16200/cs/idcplg?IdcService=COSIGN_CHECKIN_SIGNEDDOCUMENT)
			CoSign.Url.fileUploadURL - Not implemented
			CoSign.Url.notificationHandleURL - Not implemented
			CoSign.Logic.changePass - (boolean) Flag to determine if user will be asked to change password
					everytime they visit CoSign.  True - triggers change password functionality in CoSign.
			CoSign.Logic.uploadImage - (boolean) Flag to determine if user will be asked to upload signature
					image or if one will be created based on their name.  True - triggers upload image functionality
					in CoSign.
			CoSign.Logic.workingMode - (pull/push) Presently only Pull is supported.  Flag to determine method
					of retrieving signed file from CoSign.
			CoSign.Logic.verify - (boolean) Flag to determine if verification response will be returned rather
					than requesting it.  This is a heavy process and will affect server response times.  True - if
					CoSign.Logic.workingMode is set to pull will return verification meta in pull response.
			useWCCUserName - (boolean) Flag to determine if the username sent comes from WCC.  If false and
					CoSign.Auth.username is set, this user will alway be the signer of the documents.  If false and
					CoSign.Auth.username is left blank, every request to sign a document will require authentication
					on the CoSign server.  Do not set to True if CoSign.Auth.username has been set (not supported).
			CoSign.Auth.username - CoSign username to use for document signing.  Blank - user(s) will be
					authentication challenged to begin Web Signing Ceremony.
			CoSign.Auth.password - CoSign password to use for document signing.  Blank - user(s) will be
					authentication challenged to begin Web Signing Ceremony.

		WCC configurations
			coSignSecurityGroup - Security Group to hide profile content in (i.e. CoSign)
			coSignSignatureProfileMetaField - Meta field that will supply the Profile Tag names for
					profile/rule user (i.e. xCoSignSignatureTag)