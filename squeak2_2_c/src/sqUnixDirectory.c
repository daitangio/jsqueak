/* This file is a heavily modified version of the file "sqMacDicrectory.c".
 *
 * Modifications by: Ian Piumarta (ian.piumarta@inria.fr)
 *
 * The original version of this file can be regenerated from the Squeak
 * image by executing:
 *
 * 	InterpreterSupportCode writeMacSourceFiles
 *
 * $Log: sqUnixDirectory.c,v $
 * Revision 1.1  1996/10/24  13:21:38  piumarta
 * Initial revision
 *
 */

static char rcsid[]=
  "$Id: sqUnixDirectory.c,v 1.1 1996/10/24 13:21:38 piumarta Exp piumarta $";

#include "sq.h"

#include <dirent.h>
#include <sys/param.h>
#include <sys/types.h>
#include <sys/stat.h>

/***
	The interface to the directory primitive is path based.
	That is, the client supplies a Squeak string describing
	the path to the directory on every call. To avoid traversing
	this path on every call, a cache is maintained of the last
	path seen, along with the Mac volume and folder reference
	numbers corresponding to that path.
***/

/*** Constants ***/
#define ENTRY_FOUND     0
#define NO_MORE_ENTRIES 1
#define BAD_PATH        2

#define DELIMITER '/'

/*** Variables ***/
char lastPath[MAXPATHLEN+1];
int  lastPathValid = false;
int  lastIndex;
DIR *openDir;


/*** Functions ***/
int convertToSqueakTime(int unixTime);
int equalsLastPath(char *pathString, int pathStringLength);
int recordPath(char *pathString, int pathStringLength, int refNum, int volNum);
int maybeOpenDir(char *unixPath);


int convertToSqueakTime(int unixTime)
{
  /* Squeak epoch is Jan 1, 1901.  Unix epoch is Jan 1, 1970: 17 leap years
     and 52 non-leap years later than Squeak. */
  return (unsigned long)unixTime + ((52*365UL + 17*366UL) * 24*60*60UL);
}

int dir_Create(char *pathString, int pathStringLength)
{
  /* Create a new directory with the given path. By default, this
     directory is created relative to the cwd. */
  char name[1024];
  int i;
  for (i = 0; i < pathStringLength; i++)
    name[i] = pathString[i];
  name[i] = 0; /* string terminator */
  return mkdir(name, 0777) == 0;	/* rwxrwxrwx & ~umask */
}

int dir_Delimitor(void)
{
  return DELIMITER;
}

int dir_Lookup(char *pathString, int pathStringLength, int index,
/* outputs: */ char *name, int *nameLength, int *creationDate, int *modificationDate,
	       int *isDirectory, int *sizeIfFile)
{
  /* Lookup the index-th entry of the directory with the given path, starting
     at the root of the file system. Set the name, name length, creation date,
     creation time, directory flag, and file size (if the entry is a file).
     Return:	0 	if a entry is found at the given index
     		1	if the directory has fewer than index entries
		2	if the given path has bad syntax or does not reach a directory
  */

  int i;
  int nameLen= 0;
  struct dirent *dirEntry= 0;
  char unixPath[MAXPATHLEN+1];
  struct stat statBuf;

  /* default return values */
  *name             = 0;
  *nameLength       = 0;
  *creationDate     = 0;
  *modificationDate = 0;
  *isDirectory      = false;
  *sizeIfFile       = 0;

  if ((pathStringLength == 0))
    strcpy(unixPath, ".");
  else
    {
      for (i= 0; i < pathStringLength; i++)
	unixPath[i]= pathString[i];
      unixPath[i]= 0;
    }

  /* get file or directory info */
  if (!maybeOpenDir(unixPath))
    {
      return BAD_PATH;
    }

  if (++lastIndex == index)
    index= 1;		/* fake that the dir is rewound and we want the first entry */
  else
    rewinddir(openDir);	/* really rewind it, and read to the index */

  for (i= 0; i < index; i++)
    {
    nextEntry:
      dirEntry= readdir(openDir);
      if (!dirEntry)
	{
	  return NO_MORE_ENTRIES;
	}
#ifdef HAS_D_NAMLEN
      nameLen= dirEntry->d_namlen;
#else
      nameLen= strlen(dirEntry->d_name);
#endif
      /* ignore '.' and '..' (these are not *guaranteed* to be first) */
      if (nameLen < 3 && dirEntry->d_name[0] == '.')
	if (nameLen == 1 || dirEntry->d_name[1] == '.')
	  goto nextEntry;
    }

  strncpy(name, dirEntry->d_name, nameLen);
  *nameLength= nameLen;

  {
    char terminatedName[MAXPATHLEN];
    strncpy(terminatedName, dirEntry->d_name, nameLen);
    terminatedName[nameLen]= '\0';
    strcat(unixPath, "/");
    strcat(unixPath, terminatedName);
    if (stat(unixPath, &statBuf) && lstat(unixPath, &statBuf))
      {
	return BAD_PATH;
      }
  }

  /* use modification time instead (just like ls) */
  *creationDate= convertToSqueakTime(statBuf.st_mtime);
  /* use status change time instead */
  *modificationDate= convertToSqueakTime(statBuf.st_ctime);

  if (S_ISDIR(statBuf.st_mode))
    *isDirectory= true;
  else
    *sizeIfFile= statBuf.st_size;

  return ENTRY_FOUND;
}

int maybeOpenDir(char *unixPath)
{
  /* if the last opendir was to the same directory, re-use the directory
     pointer from last time.  Otherwise close the previous directory,
     open the new one, and save its name.  Return true if the operation
     was successful, false if not. */
  if (!lastPathValid || strcmp(lastPath, unixPath))
    {
      /* invalidate the old, open the new */
      if (lastPathValid)
	closedir(openDir);
      lastPathValid= false;
      strcpy(lastPath, unixPath);
      if ((openDir= opendir(unixPath)) == 0)
	return false;
      lastPathValid= true;
      lastIndex= 0;	/* first entry is index 1 */
    }
  return true;
}

int dir_SetMacFileTypeAndCreator(char *filename, int filenameSize,
				 char *fType, char *fCreator)
{
  /* unix files are untyped, and the creator is correct by default */
  return true;
}
