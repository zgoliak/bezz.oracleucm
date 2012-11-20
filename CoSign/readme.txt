CoSign Component

TODO:
Develop preferences

This component integrates WCC's content management and business processing with CoSign's digital
signature functionality.

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
		/ SignatureStatus          | Text    | T     | Valid      /
		/                          |         |       | Invalid    /
		/                          |         |       | Not Signed /
		/ Signer                   | Text    | T     |            /
		/ SignTime                 | Date    | T     |            /
		/ SignatureCount           | Integer | T     |            /
		/ CoSignRequiredSignatures | Text    | T     | UD         /
		/ CoSignSignatureTag       | Text    | T     |            /
		/ CoSignSignatureReasons   | Text    | F     | UD         /
		///////////////////////////////////////////////////////////
		UD = User Defined

Rule(s):
	CoSign_RO (Global)
		/////////////////////////////////
		/ Name                   | Type /
		/================================
		/ xSignatureStatus       | I    /
		/ xSigner                | I    /
		/ xSignTime              | I    /
		/ xSignatureCount        | I    /
		/////////////////////////////////

	CoSign_EX (Global)
		/////////////////////////////////
		/ Name                   | Type /
		/===============================/
		/ CoSignSignatureReasons | EX   /
		/////////////////////////////////