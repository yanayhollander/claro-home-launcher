package com.clarohome.feature.safety.media.apps

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MediaLibraryTest {
    @Test
    fun referencedPhotoRequiresReadGrant() {
        val library = MediaLibrary()
        val photo = referencedPhoto()

        assertThat(
            library.addReferenced(
                photo,
                MediaUriGrant(read = false, write = false, persistable = false),
            ),
        ).isFalse()
        assertThat(library.all()).isEmpty()

        assertThat(
            library.addReferenced(
                photo,
                MediaUriGrant(read = true, write = false, persistable = true),
            ),
        ).isTrue()
        assertThat(library.all()).containsExactly(photo)
    }

    @Test
    fun removingReferenceNeverRequestsDeviceDeletion() {
        val library = MediaLibrary(listOf(referencedPhoto()))

        assertThat(library.requestRemoval("picked")).isEqualTo(
            PhotoRemovalRequest.RemoveReference("picked"),
        )
        assertThat(library.confirmReferenceRemoval("picked")).isTrue()
        assertThat(library.all()).isEmpty()
    }

    @Test
    fun ownedPhotoRemainsWithoutDeleteAuthorization() {
        val photo = ClaroPhoto(
            id = "owned",
            title = "Camera photo",
            contentUri = "content://media/owned",
            ownership = PhotoOwnership.CLARO_OWNED,
        )
        val library = MediaLibrary(listOf(photo))

        assertThat(library.requestRemoval("owned")).isEqualTo(
            PhotoRemovalRequest.RequestOwnedDeleteAuthorization(
                photoId = "owned",
                contentUri = "content://media/owned",
            ),
        )
        assertThat(library.confirmOwnedDeletion("owned", authorized = false)).isFalse()
        assertThat(library.all()).containsExactly(photo)

        assertThat(library.confirmOwnedDeletion("owned", authorized = true)).isTrue()
        assertThat(library.all()).isEmpty()
    }

    private fun referencedPhoto() = ClaroPhoto(
        id = "picked",
        title = "Picked photo",
        contentUri = "content://picker/picked",
        ownership = PhotoOwnership.REFERENCED,
    )
}
