import groovy.xml.StreamingMarkupBuilder
import groovy.xml.XmlUtil

if (this.args.size() <1 ) {
    println '''
    ######
    
    ERROR: no parameters detected. Please put the script in your project folder
    
    Example for Windows:
    "C:/youFolder/yourProject/src"
    groovy permissionChecker_v1.2.groovy src
    
    Example for Mac:
    "/Users/thisUser/yourFolder/yourProject/src"
    groovy permissionChecker_v1.2.groovy src
    
    ######
    '''
    return
}

Set<String> objFieldMap = []
String srcPath = this.args[0]
def srcFolder = new File(srcPath)
def pageFolder = new File(srcPath + '/pages')
def classFolder = new File(srcPath + '/classes')
def profileFolder = new File(srcPath + '/profiles')
def psFolder = new File(srcPath + '/permissionsets')
def objFolder = new File(srcPath + '/objects')
def layoutFolder = new File(srcPath + '/layouts')
def xs = new XmlSlurper(false, false, false)
def objRtMap = []
def objList = []
def layList = []
def classList = []
def pageList = []
def delProfList = [:]
def delPermList = [:]
def delLayList = [:]
//def change = false


def checkValidPath(folder, profile, ps, obj, lay, cls, page) {
    if(!folder || !folder.isDirectory()) {
        println " ####  ERROR: ${folder} doesn't exist or is not a folder"
        System.exit(-1)
    } else {
        println " ->  Path is correct! Checking folders"
        if(!profile || !profile.isDirectory() && !ps || !ps.isDirectory() && !obj || !obj.isDirectory() &&
                !lay || !lay.isDirectory()  && !cls || !cls.isDirectory() && !page || !page.isDirectory()) {
                println " ####  ERROR: Path doesn't exist or is not a folder"
                System.exit(-2)
        }
    }
}


objFolder.eachFile { file ->
    def objectName = file.getName().split("\\.")[0]
    if(objectName.toLowerCase() == 'activity') {
        objFieldMap.addAll(xs.parse(file).fields.fullName.collect {"Task.${it.text()}"})
        objFieldMap.addAll(xs.parse(file).fields.fullName.collect {"Event.${it.text()}"})
        objList.add(objectName)
    } else {
        objFieldMap.addAll(xs.parse(file).fields.fullName.collect { "${objectName}.${it.text()}" })
        objRtMap.addAll(xs.parse(file).recordTypes.fullName.collect { "${objectName}.${it.text()}" })
        objList.add(objectName)
    }
}


layoutFolder.eachFile { file ->
    def layName = file.getName().split("\\.")[0]
    layList.add(layName)
}

def addName2List(folder, itemList, endsWith) {
    folder.eachFile { file ->
        if(file.name.endsWith(endsWith)) {
            def thisName = file.getName().split("\\.")[0]
            itemList.add(thisName)
        }
    }
}

def searchFiles(folder, ofield, rta, obj, xs, layout, onlyCustom, cls, page) {
    def delList = [:]
    folder.eachFile { file ->
        def hasChanges = false
        delList.put(file.name, [
                fields: [], recordTypes: [], objects: [], layouts: [], classes: [], pages: []
        ])
        for(field in xs.parse(file).fieldPermissions.field.collect { it.text() }) {
            if(!ofield.find { it == field }) {
                delList[file.name].fields.add(field)
                hasChanges = true
            }
        }
        for(field in xs.parse(file).recordTypeVisibilities.recordType.collect { it.text() }) {
            if(!rta.find { it == field }) {
                delList[file.name].recordTypes.add(field)
                hasChanges = true
            }
        }
        for(field in xs.parse(file).objectPermissions.object.collect { it.text() }) {
            if(!obj.find { it == field }) {
                delList[file.name].objects.add(field)
                hasChanges = true
                }
            }
        for(field in xs.parse(file).layoutAssignments.layout.collect { it.text() }) {
            if(!layout.find { it == field }) {
                delList[file.name].layouts.add(field)
                hasChanges = true
            }
        }
        for(field in xs.parse(file).classAccesses.apexClass.collect { it.text() }) {
            if(!cls.find { it == field }) {
                delList[file.name].classes.add(field)
                hasChanges = true
            }
        }
        for(field in xs.parse(file).pageAccesses.apexPage.collect { it.text() }) {
            if(!page.find { it == field }) {
                delList[file.name].pages.add(field)
                hasChanges = true
            }
        }
        if (onlyCustom) {
            delList[file.name].fields.retainAll{it.toLowerCase().contains('__c')}
            delList[file.name].objects.retainAll{it.toLowerCase().contains('__c')}
            if(!delList[file.name].fields && !delList[file.name].recordTypes && !delList[file.name].objects &&
                    !delList[file.name].layouts && !delList[file.name].classes && !delList[file.name].pages) {
                hasChanges = false
            }
        }
        if (!hasChanges) {
            delList.remove(file.name)
        }
    }
    return delList
}


def displayDeleted(delList) {
    if(!delList.isEmpty()){
        println """
        ###  To delete :
        """
        delList.each { k, v ->
            println "-> ${k}"
            if(v.fields.size() > 0 ) { println      "     Fields : ${v.fields}"}
            if(v.recordTypes.size() > 0 ) { println "     recordTypes : ${v.recordTypes}"}
            if(v.objects.size() > 0 ) { println     "     Objects : ${v.objects}"}
            if(v.layouts.size() > 0 ) { println     "     Layouts : ${v.layouts}"}
            if(v.classes.size() > 0 ) { println     "     Classes : ${v.classes}"}
            if(v.pages.size() > 0 ) { println       "     Pages : ${v.pages}"}
            println " "
            }
        }
}



def updateRecords(delList, folder, xs) {
    delList.each { key, value ->
        def record = xs.parse(new File(folder.path  + "/" + key))
        if (value) {
            record.fieldPermissions.each { fieldPermission ->
                if (fieldPermission.field.text() in value.fields) {
                    fieldPermission.replaceNode {}
                }
            }
            record.recordTypeVisibilities.each { rtv ->
                if (rtv.recordType.text() in value.recordTypes) {
                    rtv.replaceNode {}
                }
            }
            record.objectPermissions.each { obj ->
                if (obj.object.text() in value.objects) {
                    obj.replaceNode {}
                }
            }
            record.layoutAssignments.each { lay ->
                if(lay.layout.text() in value.layouts) {
                    lay.replaceNode{}
                }
            }
            record.classAccesses.each { cls ->
                if(cls.apexClass.text() in value.classes) {
                    cls.replaceNode{}
                }
            }
            record.pageAccesses.each { page ->
                if(page.apexPage.text() in value.pages) {
                    page.replaceNode{}
                }
            }

            def writer = new OutputStreamWriter(new FileOutputStream(folder.path + '/' + key), 'UTF-8')
            def printer = new XmlNodePrinter(new PrintWriter(writer), "    ")
            printer.preserveWhitespace = true
            def builder = new StreamingMarkupBuilder(encoding: "UTF-8", useDoubleQuotes: true)
            def thisRecord = builder.bind {
                mkp.xmlDeclaration(["version":"1.0", "encoding":"UTF-8"])
                mkp.yield record
            }

            def xml = new XmlParser().parseText(XmlUtil.serialize(thisRecord))
            writer << '<?xml version="1.0" encoding="UTF-8"?>\n'
            printer.print(xml)
        }
    }

}






def yesNo (delProfList, delPermList, console, profileFolder, psFolder, xs) {
    displayDeleted(delProfList)
    displayDeleted(delPermList)
    def delYesNo = console.readLine('-> Do you wish to delete all files? [Y]/[N]').charAt(0).toLowerCase().toString()
    if(delYesNo == 'y') {
        updateRecords(delProfList, profileFolder, xs)
        updateRecords(delPermList, psFolder, xs)
        println "        - - - - ###  Deletion completed  - - - -  "
    }
}

//  ###  Starting the script

checkValidPath(srcFolder, profileFolder, psFolder, objFolder, layoutFolder, classFolder, pageFolder)

def console = System.console()
def delCustom = console.readLine('-> Do you want to skip Standard and use only Custom? [Y]/[N]').charAt(0).toLowerCase().toString()
if(delCustom == 'y') {
    addName2List(classFolder, classList, '.cls')
    addName2List(pageFolder, pageList, '.page')
    delProfList = searchFiles(profileFolder, objFieldMap, objRtMap, objList, xs, layList, true, classList, pageList)
    delPermList = searchFiles(psFolder, objFieldMap, objRtMap, objList, xs, null, true, classList, pageList)
    if(delPermList || delProfList) {
        yesNo (delProfList, delPermList, console, profileFolder, psFolder, xs)
    } else {
        println " ###  Nothing to delete"
    }
} else {
    addName2List(classFolder, classList, '.cls')
    addName2List(pageFolder, pageList, '.page')
    delProfList = searchFiles(profileFolder, objFieldMap, objRtMap, objList, xs, layList, false, classList, pageList)
    delPermList = searchFiles(psFolder, objFieldMap, objRtMap, objList, xs, null, false, classList, pageList)
    if(delPermList || delProfList) {
        yesNo (delProfList, delPermList, console, profileFolder, psFolder, xs)
    } else {
        println " ###  Nothing to delete"
    }
}




